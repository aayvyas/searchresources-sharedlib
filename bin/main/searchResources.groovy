#!/usr/bin/env groovy

@Grapes([
    @Grab(group='com.google.api.grpc', module='grpc-google-cloud-asset-v1', version='3.14.0', scope='test'),
    @Grab(group='com.google.api.grpc', module='proto-google-cloud-asset-v1', version='3.14.0'),
    @Grab(group='com.google.cloud', module='google-cloud-asset', version='3.14.0'),
    @Grab(group='com.google.api.grpc', module='proto-google-cloud-asset-v1p7beta1', version='3.14.0'),
    @Grab(group='com.google.api', module='gax-grpc', version='2.23.0'),
    @Grab(group='org.apache.commons', module='commons-csv', version='1.10.0'),
    @Grab(group='com.google.cloud', module='google-cloud-storage', version='2.18.0'),
    // @GrabConfig( systemClassLoader=true)
])

// import org.apache.commons.csv.CSVPrinter
import org.apache.commons.csv.CSVFormat
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.InvalidArgumentException;
import com.google.cloud.asset.v1.AssetServiceClient;
import com.google.cloud.asset.v1.AssetServiceClient.SearchAllResourcesPagedResponse;
import com.google.cloud.asset.v1.SearchAllResourcesRequest;
// import groovy.yaml.YamlSlurper

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.nio.file.Paths;

    // Searches for all the resources within the given scope.
    def searchAllResources(def settings) {
        List resourcesList = []
        List supportedAssetTypes
        List excludedAssetTypes

        // list of all supported assetTypes in gcp    
        supportedAssetTypes = settings.supportedAssetTypes
        // list of the irrelavant assetTypes which you want to be excluded
        excludedAssetTypes = settings.excludedAssetTypes
    
        println "Getting Projects information ... "

        // a map of project number to project id pre-processed
        def pNtoId = projectNoToId(settings.scope)

        List assetTypes = supportedAssetTypes -excludedAssetTypes
        int pageSize = 500;
        String pageToken = "";
        String orderBy = settings.orderBy==null ? "" : settings.orderBy ;

        SearchAllResourcesRequest request =
            SearchAllResourcesRequest.newBuilder()
                .setScope(settings.scope)
                .setQuery(settings.query ?: "")
                .addAllAssetTypes(assetTypes)
                .setPageSize(pageSize)
                .setPageToken(pageToken)
                .setOrderBy(orderBy)
                .build();
	    println request
        println "Sending request ..."
	    try{
        	AssetServiceClient client = AssetServiceClient.create() 
	        SearchAllResourcesPagedResponse response = client.searchAllResources(request);
            	resourcesList += response.getPage().getValues()
                
            while( !response.getNextPageToken().isEmpty()){
                request = request.toBuilder().setPageToken(response.getNextPageToken()).build();
                response = client.searchAllResources(request);
                resourcesList +=response.getPage().getValues()
            }
            // setting to null for deserialization error LazyMap
            response = null
            request = null
            client = null
            def resources = []
            resourcesList.eachWithIndex{ entry, idx -> 
                def convertLabelsToString = { e -> 
                    if( "${e.labels}" == "[:]"){
                        return "no labels"
                    }
                    String labels = ""
                    e.labels.each{ label ->
                        if(label.value == ""){
                            labels+="${label.key}, "
                        }else{
                            labels+="${label.key}:${label.value}, "
                        }
                    }
                    
                    return labels
                }
                def extractState = { state ->
                    if(state ==~ '/*'){
                        return "NO STATE"
                    }
                    return state.toString().toUpperCase()
                }
                def resource = [entry.displayName, entry.assetType.split("/")[1], new Date(entry.createTime.seconds * 1000),extractState(entry.state), "{${convertLabelsToString(entry)}}", pNtoId[entry.project.split("/")[1]], entry.location]
                resources << resource
            }
            return resources
        
        } catch (IOException e) {
	        println "Failed to create client: ${e.toString()}";
            
        } catch (InvalidArgumentException e) {
        	println "Invalid request: ${e.toString()}";
            
        } catch (ApiException e) {
	        println "Error during SearchAllResources: ${e.toString}";
            
        } 
    }
    def generateCsv(List resources, String fileName){
        println "Generating csv file..."

        // Building the csv record
        def csvData = [["name", "resource_type", "createTime", "state", "labels", "project_no", "location"]]
        resources.each{ resource ->
            csvData << resource
        }
        writeCSV(file: fileName, records: csvData, format: org.apache.commons.csv.CSVFormat.DEFAULT)
        println "csv generated successfully ${fileName}"
             
        // CSVPrinter printer = new CSVPrinter(
        //     new PrintWriter(fileName),
        //     CSVFormat.DEFAULT
        // )
        // csvData.each {
        //     printer.printRecord(it)
        // }

        // printer.close()
    } 

    /**
     * @param scope from settings.yaml
     * @return map[projectNumber] : projectId
     */
    def projectNoToId(String scope){
        List resourcesList = []
        int pageSize = 500;
        String pageToken = "";
        String orderBy = "";
        def scopeResource = scope.split('/')[1] == "Folders" ? "Folder" : "Project"
        def assetTypes = ["cloudresourcemanager.googleapis.com/${scopeResource}".toString()]
        SearchAllResourcesRequest request =
            SearchAllResourcesRequest.newBuilder()
                .setScope(scope)
                .setQuery("")
                .addAllAssetTypes(assetTypes)
                .setPageSize(pageSize)
                .setPageToken(pageToken)
                .setOrderBy(orderBy)
                .build();
	    try{
        	AssetServiceClient client = AssetServiceClient.create() 
	        SearchAllResourcesPagedResponse response = client.searchAllResources(request);
            	resourcesList += response.getPage().getValues()
            
            while( !response.getNextPageToken().isEmpty()){
                request = request.toBuilder().setPageToken(response.getNextPageToken()).build();
                response = client.searchAllResources(request);
                resourcesList +=response.getPage().getValues()
            }
            def pNtoId = new HashMap()
            resourcesList.each{ resource ->
                pNtoId[resource.project.split('/')[1]] = resource.additionalAttributes.fields["projectId"].stringValue
            }
            
            // setting to null for deserialization error
            response = null
            request = null
            client = null

            return pNtoId
            
        } catch (IOException e) {
	        println "Failed to create client: ${e.toString()}";
        } catch (InvalidArgumentException e) {
        	println "Invalid request: ${e.toString()}";
        } catch (ApiException e) {
	        println "Error during SearchAllResources: ${e.toString}";
        } 
    }

    // looks for resource path and pushes that to bucket
    void pushToBucket(String filePath,String fileName ,String bucketName){
        Storage storage = StorageOptions.getDefaultInstance().getService();
        println "creating a bucket..."
        // Create a bucket
        try{
            Bucket bucket = storage.create(BucketInfo.of(bucketName));
            println "Successfully Created ${bucketName}!!!"
        }catch(Exception e){
            println e.message
        }
        
        // Upload a blob to the newly created bucket
        
        BlobId blobId = BlobId.of(bucketName, fileName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("text/plain").build();
        // byte[] content = filePath.getBytes(StandardCharsets.UTF_8);
        // storage.createFrom(blobInfo, new ByteArrayInputStream(content));
        storage.createFrom(blobInfo, Paths.get(filePath))
        println "Uploaded Successfully!!!"
    }
    def call(){
        def resources = []
        
        // reading settings.yaml
	    def settingsFile  = libraryResource "settings.yaml"
	    // settings = new YamlSlurper().parseText(settingsFile)
        def settings = readYaml(text: settingsFile)
        // def fileName = "${settings.scope.split("/")[1]}.csv".toString()
        /* 
        TODO: remove below fileName when moving to LBG
        */
        def fileName = "resources.csv"
        def bucketName = settings.bucketName

        
        node{

            stage("Search Resources"){
            
                script{
                    resources = searchAllResources(settings)
                }
                
            }
        }
        node{
            stage("Generate CSV"){        
                script{
                    generateCsv(resources,fileName)
                }

            }
        }
        node{
            stage("Push to bucket"){
            
                pushToBucket("./${WORKSPACE}/${fileName}",fileName,bucketName)    
            
            }
        }
            

        
    }
