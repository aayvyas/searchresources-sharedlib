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
class SearchForResources {
    List resourcesList = ["identity.accesscontextmanager.googleapis.com/AccessLevel",
                          "identity.accesscontextmanager.googleapis.com/AccessPolicy",
                          "identity.accesscontextmanager.googleapis.com/ServicePerimeter",
                          "anthos.googleapis.com/ConnectedCluster",
                          "apigateway.googleapis.com/Api",
                          "apigateway.googleapis.com/ApiConfig",
                          "apigateway.googleapis.com/Gateway",
                          "apikeys.googleapis.com/Key",
                          "appengine.googleapis.com/Application",
                          "appengine.googleapis.com/Service",
                          "appengine.googleapis.com/Version",
                          "memcache.googleapis.com/Instance",
                          "artifactregistry.googleapis.com/DockerImage",
                          "artifactregistry.googleapis.com/Repository",
                          "assuredworkloads.googleapis.com/Workload",
                          "gkebackup.googleapis.com/Backup",
                          "gkebackup.googleapis.com/BackupPlan",
                          "gkebackup.googleapis.com/Restore",
                          "gkebackup.googleapis.com/RestorePlan",
                          "gkebackup.googleapis.com/VolumeBackup",
                          "gkebackup.googleapis.com/VolumeRestore",
                          "bigquery.googleapis.com/Dataset",
                          "bigquery.googleapis.com/Model",
                          "bigquery.googleapis.com/Table",
                          "privateca.googleapis.com/CaPool",
                          "privateca.googleapis.com/Certificate",
                          "privateca.googleapis.com/CertificateAuthority",
                          "privateca.googleapis.com/CertificateRevocationList",
                          "privateca.googleapis.com/CertificateTemplate",
                          "bigtableadmin.googleapis.com/AppProfile",
                          "bigtableadmin.googleapis.com/Backup",
                          "bigtableadmin.googleapis.com/Cluster",
                          "bigtableadmin.googleapis.com/Instance"
                          "bigtableadmin.googleapis.com/Table",
                          "cloudbilling.googleapis.com/BillingAccount",
                          "cloudbilling.googleapis.com/ProjectBillingInfo",
                          "composer.googleapis.com/Environment",
                          "datafusion.googleapis.com/Instance",
                          "dlp.googleapis.com/DeidentifyTemplate",
                          "dlp.googleapis.com/DlpJob",
                          "dlp.googleapis.com/InspectTemplate",
                          "dlp.googleapis.com/JobTrigger",
                          "dlp.googleapis.com/StoredInfoType",
                          "dns.googleapis.com/ManagedZone",
                          "dns.googleapis.com/Policy",
                          "domains.googleapis.com/Registration",
                          "cloudfunctions.googleapis.com/CloudFunction",
                          ""
                        

                        ]
    def settings
    private final List supportedAssetTypes= [""]
    SearchForResources() {
        def settingsFile  = new File("./settings.yaml")
        settings = new YamlSlurper().parse(settingsFile)
        println settings.scope
    }
    // Searches for all the resources within the given scope.
    public void searchAllResources() {
        // Specify the types of resources that you want to be listed
        // supported types: https://cloud.google.com/asset-inventory/docs/supported-asset-types
        List assetTypes = ["compute.googleapis.com/Instance",
                           "compute.googleapis.com/Disk",
                           "compute.googleapis.com/Snapshot",
                           "container.googleapis.com/Cluster",
                           "container.googleapis.com/NodePool",
                           "storage.googleapis.com/Bucket",
                           ];
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
                def resource = [entry.displayName, entry.assetType, new Date(entry.createTime.seconds * 1000),entry.state, "{${convertLabelsToString(entry)}}", entry.project]
                resources << resource
                println entry
            }
            convertToCsv(resources)

        } catch (IOException e) {
                println "Failed to create client: ${e.toString()}";
        } catch (InvalidArgumentException e) {
                println "Invalid request: ${e.toString()}";
        } catch (ApiException e) {
                println "Error during SearchAllResources: ${e.toString}";
        }
    }
    private def convertToCsv(List resources){
        def csvData = [["name", "resource_type", "createTime", "state", "labels", "project_id"]]
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
    private def readSettings(){
    }

}
SearchForResources searchForResources = new SearchForResources()
searchForResources.searchAllResources() // generates project.csv