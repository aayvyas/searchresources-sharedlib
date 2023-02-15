import spock.lang.Specification

class SearchResourcesTest extends Specification{

    def "Example Test"(){
        when:
        searchResources searchResources = new searchResources()
        searchResources.call()
        then:
        1==1

    }


}

