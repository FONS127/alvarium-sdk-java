@Library('alvarium-pipelines') _

pipeline {
    agent any
    tools {
        maven 'M3'
    }
    stages {
        stage('prep - generate source code checksum') {
            steps {
                sh 'mkdir -p $JENKINS_HOME/jobs/$JOB_NAME/$BUILD_NUMBER/'
                sh ''' find /var/lib/jenkins/jobs/alvarium-sdk-java/77 -type f -exec md5sum {} + | LC_ALL=C sort | md5sum |\
                        cut -d" " -f1 \
                        > $JENKINS_HOME/jobs/$JOB_NAME/$BUILD_NUMBER/sc_checksum
                '''
            }
        }

        // stage('test') {
        //     steps {
        //         sh 'mvn test'
        //     }
        //     post {
        //         success {
        //             junit 'target/surefire-reports/**/*.xml'
        //         }
        //     }
        // }

        stage('alvarium - pre-build annotations') {
            steps {
                sh 'cat $JENKINS_HOME/jobs/$JOB_NAME/$BUILD_NUMBER/sc_checksum'
                sh 'find /var/lib/jenkins/jobs/alvarium-sdk-java/77 -type f -exec md5sum {} + | LC_ALL=C sort | md5sum'
                sh 'pwd'
                script{
                    def optionalParams = ['sourceCodeChecksumPath':"${JENKINS_HOME}/jobs/${JOB_NAME}/${BUILD_NUMBER}/sc_checksum"]
                    alvariumCreate(['source-code', 'vulnerability'],optionalParams)
                }
            }
        }

        stage('build') {
            steps {
                sh 'mvn package'
            }
            post {
                success {
                    archiveArtifacts artifacts: 'target/**/*.jar', fingerprint: true

                    // Generate artifact checksums
                    sh ''' for f in target/*.jar;
                    do
                        mkdir -p $JENKINS_HOME/jobs/$JOB_NAME/$BUILD_NUMBER/
                        md5sum $f | cut -d ' ' -f 1 | tr 'a-z' 'A-Z' | tr -d '\n' \
                            > $JENKINS_HOME/jobs/$JOB_NAME/$BUILD_NUMBER/$(basename $f).checksum
                    done
                    '''

                    // Check if artifact has a valid checksum... Ideally this
                    // should be done by whatever is pulling the artifact but
                    // alvarium currently has no way of persisting information
                    // relating to annotator logic, which is why the checksum is
                    // being fetched from the file system instead of a persistent
                    // store
                    // TODO (Ali Amin): Find a way to persist the checksum
                    script {
                        def artifactChecksum = readFile "/${JENKINS_HOME}/jobs/${JOB_NAME}/${BUILD_NUMBER}/alvarium-sdk-1.0-SNAPSHOT.jar.checksum"
                        def optionalParams = ["artifactPath":"${WORKSPACE}/target/alvarium-sdk-1.0-SNAPSHOT.jar", "checksumPath": "/${JENKINS_HOME}/jobs/${JOB_NAME}/${BUILD_NUMBER}/alvarium-sdk-1.0-SNAPSHOT.jar.checksum"]
                        alvariumMutate(['checksum'], optionalParams, artifactChecksum.bytes)
                    }   
                }
            }
        }
    }
}
