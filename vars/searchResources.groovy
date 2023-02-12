#!/usr/bin/env groovy

@Grapes(
        @Grab(group='com.google.api.grpc', module='grpc-google-cloud-asset-v1', version='3.14.0', scope='test')
)
@Grapes(
        @Grab(group='com.google.api.grpc', module='proto-google-cloud-asset-v1', version='3.14.0')
)
@Grapes(
        @Grab(group='com.google.cloud', module='google-cloud-asset', version='3.14.0')
)
@Grapes(
        @Grab(group='com.google.api.grpc', module='proto-google-cloud-asset-v1p7beta1', version='3.14.0')
)
@Grapes(
        @Grab(group='com.google.api', module='gax-grpc', version='2.23.0')
)
@Grapes(
    @Grab(group='org.apache.commons', module='commons-csv', version='1.10.0')
)
@Grapes(
    @Grab(group='org.codehaus.groovy', module='groovy-yaml', version='3.0.14')
)
@Grapes(
    @Grab(group='com.google.cloud', module='google-cloud-storage', version='2.18.0')
)

import org.apache.commons.csv.CSVPrinter
import org.apache.commons.csv.CSVFormat
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.InvalidArgumentException;
import com.google.cloud.asset.v1.AssetServiceClient;
import com.google.cloud.asset.v1.AssetServiceClient.SearchAllResourcesPagedResponse;
import com.google.cloud.asset.v1.SearchAllResourcesRequest;
import groovy.yaml.YamlSlurper

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
    void searchAllResources() {


        List resourcesList = []
        def settings
        List supportedAssetTypes
        List excludedAssetTypes

	    def settingsFile  = libraryResource "settings.yaml"
	    // settings = new YamlSlurper().parseText(settingsFile)
        
        settings = readYaml(text: settingsFile)
        
        
        supportedAssetTypes = settings.supportedAssetTypes
        excludedAssetTypes = settings.excludedAssetTypes
        // Specify the types of resources that you want to be listed
        
        println "Getting Projects information ... "
        def pNtoId = listAllInScope(settings.scope)
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
            response = null
            request = null
            client = null
            List resources = []
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
                def resource = [entry.displayName, entry.assetType.split("/")[1], new Date(entry.createTime.seconds * 1000),entry.state, "{${convertLabelsToString(entry)}}", pNtoId[entry.project.split("/")[1]], entry.location]
                resources << resource
            }
            // converting the resourcesList to .csv
            def fileName = "resources.csv"
            convertToCsv(resources, fileName)
            pushToBucket("./${WORKSPACE}/${fileName}")
            
            
        
        } catch (IOException e) {
	        println "Failed to create client: ${e.toString()}";
        } catch (InvalidArgumentException e) {
        	println "Invalid request: ${e.toString()}";
        } catch (ApiException e) {
	        println "Error during SearchAllResources: ${e.toString}";
        } 
    }
    def convertToCsv(List resources, String fileName){
        println "Generating csv file..."
        def csvData = [["name", "resource_type", "createTime", "state", "labels", "project_no", "location"]]
        resources.each{ resource ->
            csvData << resource
        }
        
        writeCSV(file: fileName, records: csvData, format: org.apache.commons.csv.CSVFormat.DEFAULT)
        println "csv generated successfully location ${fileName}"
        
        
        
        
        
        
        // CSVPrinter printer = new CSVPrinter(
        //     new PrintWriter(fileName),
        //     CSVFormat.DEFAULT
        // )
        // csvData.each {
        //     printer.printRecord(it)
        // }

        // printer.close()
    } 
    def listAllInScope(String scope){
        List resourcesList = []
        int pageSize = 500;
        String pageToken = "";
        String orderBy = "";
        String scopeResource = "" //scope.split('/')[1] == "Folders" ? "Folder" : "Project"
        List assetTypes = ["cloudresourcemanager.googleapis.com/Project"]
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
    void pushToBucket(String filePath){
        Storage storage = StorageOptions.getDefaultInstance().getService();
        println "creating a bucket..."
        // Create a bucket
        String bucketName = "aayvyas-assets-inventory"; // Change this to something unique
        try{
            
            Bucket bucket = storage.create(BucketInfo.of(bucketName));
            println "Successfully Created ${bucketName}!!!"
        }catch(Exception e){
            println e.message
        }
        
        // Upload a blob to the newly created bucket
        BlobId blobId = BlobId.of(bucketName, "resources.csv");
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("text/plain").build();
        // byte[] content = filePath.getBytes(StandardCharsets.UTF_8);
        // storage.createFrom(blobInfo, new ByteArrayInputStream(content));
        storage.createFrom(blobInfo, Paths.get(filePath))
        println "Uploaded Successfully!!!"
    }

    def call(){
        pipeline{
            agent any
            stages{
                stage("Search Resources"){
                    steps{
                        searchAllResources()
                    }
                    
                }
                stage("Push to bucket"){
                    steps{
                        def fileName = "resources.csv"
                        pushToBucket("./${WORKSPACE}/${fileName}")    
                    }
                    
                }
            }
        }
        
    }
