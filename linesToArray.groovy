@Grapes(
    @Grab(group='org.codehaus.groovy', module='groovy-yaml', version='3.0.14')
)

import groovy.yaml.YamlBuilder


def file = new File("./data.txt")
def line
def supportedAssetTypes = []

file.withReader { reader ->
    while((line = reader.readLine())!=null){
        
        supportedAssetTypes << line.toString().replace("\n", "")
    }
}

def result = []
supportedAssetTypes.each{ it ->
    
        if(it ==~ '/*'){
            println it
        }else{
            result << it.replace(" ","")
        }   
}

File yml = new File("supportedTypes.yaml")
def builder = new YamlBuilder()

builder { 
    supportedAssetTypes result    
}
println builder.toString()

yml << builder.toString()

    

