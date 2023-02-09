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
import org.apache.commons.csv.CSVPrinter
import org.apache.commons.csv.CSVFormat
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.InvalidArgumentException;
import com.google.cloud.asset.v1.AssetServiceClient;
import com.google.cloud.asset.v1.AssetServiceClient.SearchAllResourcesPagedResponse;
import com.google.cloud.asset.v1.SearchAllResourcesRequest;
import groovy.yaml.YamlSlurper

    
    // Searches for all the resources within the given scope.
    void searchAllResources() {
         List resourcesList = []
         def settings
        List supportedAssetTypes
    List excludedAssetTypes
    
	    def settingsFile  = new File("../resources/settings.yaml")
	    settings = new YamlSlurper().parse(settingsFile)
        supportedAssetTypes = settings.supportedAssetTypes
        excludedAssetTypes = settings.excludedAssetTypes
        // Specify the types of resources that you want to be listed
        
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
	try{
        	AssetServiceClient client = AssetServiceClient.create() 
	        SearchAllResourcesPagedResponse response = client.searchAllResources(request);
            	resourcesList += response.getPage().getValues()
            
            while( !response.getNextPageToken().isEmpty()){
                request = request.toBuilder().setPageToken(response.getNextPageToken()).build();
                response = client.searchAllResources(request);
                resourcesList +=response.getPage().getValues()
            }
            println resourcesList
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
                def resource = [entry.displayName, entry.assetType, new Date(entry.createTime.seconds * 1000),entry.state, "{${convertLabelsToString(entry)}}", entry.project.split("/")[1]]
                resources << resource
		        println entry
            }
            // converting the resourcesList to .csv
            convertToCsv(resources)
        
        } catch (IOException e) {
	        println "Failed to create client: ${e.toString()}";
        } catch (InvalidArgumentException e) {
        	println "Invalid request: ${e.toString()}";
        } catch (ApiException e) {
	        println "Error during SearchAllResources: ${e.toString}";
        } 
    }
    def convertToCsv(List resources){
        def csvData = [["name", "resource_type", "createTime", "state", "labels", "project_no"]]
        resources.each{ resource ->
            csvData << resource
        }
        println csvData
        
        CSVPrinter printer = new CSVPrinter(
            new PrintWriter("resources.csv"),
            CSVFormat.DEFAULT
        )
        csvData.each {
            printer.printRecord(it)
        }

        printer.close()
    } 

def call(){
    searchAllResources()
}

