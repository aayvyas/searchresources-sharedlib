@Library('searchResources')_


pipeline {
    agent any 
    triggers { cron('H */6 */1 * *') }
    stages {
        
        stage("not part of sharedlib"){
            steps{
                sh "echo not part of sharedlib"    
            }
            
        }
    
    
        stage("part of sharedlib"){
            steps{
                searchResources()                
            }
        }
    }
}




