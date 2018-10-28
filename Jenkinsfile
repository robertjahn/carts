def replaceImageName(new_image_name, file) {
   sh "sed -i 's#image: .*#image: $new_image_name#' $file"
}

pipeline {
  agent any
  environment {

    // Assumes Jenkins Environment Variables set for GC_PROJECT, GC_CLUSTER, GC_ZONE, CART_SERVICE_IP

    APP_NAME = "cart"
    VERSION = readFile('version').trim()
    ARTIFACT_ID = "${env.APP_NAME}"

    DT_SERVICE_TAGNAME = "ServiceName"
    DT_SERVICE_TAGVALUE = "carts"

    //DockerHub public requires format of <account>/<repo>:<tag>
    //and does not support multiple forward slashes in the name, so must alter format
    REPOSITORY = "robjahn/${env.ARTIFACT_ID}"
    TAG_STAGING = "${env.VERSION}-SNAPHOT-${env.BUILD_NUMBER}"
    TAG_PROD = "${env.VERSION}"
	  
    // hardcoded for now 
    // can later adjust logic use kubectl get service as an approach
    SERVICE_URL = 35.243.193.99
  }	

  stages {

    stage('Maven Build') {
      steps {

          echo "REPOSITORY = ${env.REPOSITORY}"
          echo "TAG_STAGING = ${env.TAG_STAGING}"
          echo "TAG_PROD = ${env.TAG_PROD}"
          echo "SERVICE_URL = ${SERVICE_URL}"

          echo "Building branch_name: ${env.BRANCH_NAME}"
          sh "mvn -B clean package -DskipTests"
      }
    }

    stage('Checkout') {
      steps {
        // into a deployment subdirectory we checkout the kubectl deployment scripts
        dir ('sockshop-deploy') {
          withCredentials([usernamePassword(credentialsId: 'github', passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
            git url: "https://${GIT_USERNAME}:${GIT_PASSWORD}@github.com/robertjahn/sockshop-deploy.git", branch: "master"
	      }
        }

        // into a deployment subdirectory we checkout the utility scripts
        dir ('sockshop-utils') {
          git url: 'https://github.com/robertjahn/sockshop-utils.git', branch: 'master'
        }

        // into a dynatrace-cli subdirectory we checkout the Dynatrace CLI
        dir ('dynatrace-cli') {
          git url: 'https://github.com/Dynatrace/dynatrace-cli.git', branch: 'master'
        }
      }
    }
	  
    stage('Docker build and push to registry'){
      when {
        expression {
          return env.BRANCH_NAME ==~ 'release/.*' || env.BRANCH_NAME ==~'master'
        }
      }
      steps {
        script {
            def image
            image = docker.build("${env.REPOSITORY}:${env.TAG_STAGING}")
            docker.withRegistry('https://registry.hub.docker.com', 'dockerhub') {
                image.push()
            }
        }
      }
    }
    stage('Deploy to stage namespace') {
      when {
        expression {
          return env.BRANCH_NAME ==~ 'release/.*' || env.BRANCH_NAME ==~'master'
        }
      }
      steps {
        script {
          def deploy_cmd = './sockshop-utils/dynatrace-scripts/pushdeployment.sh SERVICE CONTEXTLESS ' + DT_SERVICE_TAGNAME + ' ' + DT_SERVICE_TAGVALUE +
            ' ${BUILD_TAG} ${BUILD_NUMBER} ${JOB_NAME} ${JENKINS_URL}' +
            ' ${JOB_URL} ${BUILD_URL} ${GIT_COMMIT}'
          echo deploy_cmd
          sh deploy_cmd

          replaceImageName("${env.REPOSITORY}:${env.TAG_STAGING}", "sockshop-deploy/staging/carts.yml")

          // Jenkins Credentials need to be configured with gcloud credentials
          withCredentials([file(credentialsId: 'GC_KEY', variable: 'GC_KEY')]) {
            sh "gcloud auth activate-service-account --key-file=${GC_KEY}"
            sh "gcloud container clusters get-credentials ${GC_CLUSTER} --zone ${GC_ZONE} --project ${GC_PROJECT}"
            sh "./sockshop-utils/create_namespace.sh stage"
            sh "kubectl apply -f sockshop-deploy/staging/carts.yml"
            sh "kubectl apply -f sockshop-deploy/staging/carts-svc.yml"

            echo "waiting for the service to start..."
            sleep 180
            sh "kubectl -n stage get pods -o wide"
	    sh "kubectl -n stage get service -o wide"		  
          }
        }
      }
    }
    stage('Run health check in stage') {
      when {
        expression {
          return env.BRANCH_NAME ==~ 'release/.*' || env.BRANCH_NAME ==~'master'
        }
      }
      steps {

        build job: "acm-workshop/jmeter-as-container",
          parameters: [
            string(name: 'BUILD_JMETER', value: 'no'),
            string(name: 'SCRIPT_NAME', value: 'basiccheck.jmx'),
            string(name: 'SERVER_URL', value: "${env.SERVICE_URL}"),
            string(name: 'SERVER_PORT', value: '80'),
            string(name: 'CHECK_PATH', value: '/health'),
            string(name: 'VUCount', value: '1'),
            string(name: 'LoopCount', value: '1'),
            string(name: 'DT_LTN', value: "HealthCheck_${BUILD_NUMBER}"),
            string(name: 'FUNC_VALIDATION', value: 'yes'),
            string(name: 'AVG_RT_VALIDATION', value: '0'),
            string(name: 'RETRY_ON_ERROR', value: 'yes')
          ]
      }
    }
	  
    stage('Run load test in stage') {
      when {
        expression {
          return env.BRANCH_NAME ==~ 'release/.*' || env.BRANCH_NAME ==~'master'
        }
      }
      steps {
        script {
          def start_test_cmd = './sockshop-utils/dynatrace-scripts/pushevent.sh SERVICE CONTEXTLESS '+ DT_SERVICE_TAGNAME + ' ' + DT_SERVICE_TAGVALUE +
            ' "STARTING Load Test as part of Job: " ${JOB_NAME} Jenkins-Start-Test ' +
            ' ${JOB_URL} ${BUILD_URL} ${GIT_COMMIT}'
          echo start_test_cmd
          sh start_test_cmd

          build job: "acm-workshop/jmeter-as-container",
            parameters: [
              string(name: 'BUILD_JMETER', value: 'no'),
              string(name: 'SCRIPT_NAME', value: "${env.APP_NAME}_load.jmx"),
              string(name: 'SERVER_URL', value: "${env.SERVICE_URL}"),
              string(name: 'SERVER_PORT', value: '80'),
              string(name: 'CHECK_PATH', value: '/health'),
              string(name: 'VUCount', value: '1'),
              string(name: 'LoopCount', value: '1'),
              string(name: 'DT_LTN', value: "FuncCheck_${BUILD_NUMBER}"),
              string(name: 'FUNC_VALIDATION', value: 'yes'),
              string(name: 'AVG_RT_VALIDATION', value: '0')
            ]

          def end_test_cmd = './sockshop-utils/dynatrace-scripts/pushevent.sh SERVICE CONTEXTLESS '+ DT_SERVICE_TAGNAME + ' ' + DT_SERVICE_TAGVALUE +
            ' "ENDING Load Test as part of Job: " ${JOB_NAME} Jenkins-End-Test ' +
            ' ${JOB_URL} ${BUILD_URL} ${GIT_COMMIT}'
          echo end_test_cmd
          sh end_test_cmd
        }
      }
    }

    stage('Check in Stage deployment change') {
        when {
            expression {
                return env.BRANCH_NAME ==~ 'release/.*' || env.BRANCH_NAME ==~'master'
            }
        }
        steps {
	    replaceImageName("${env.REPOSITORY}:${env.TAG_STAGING}", "sockshop-deploy/staging/carts.yml")
	    sh """
	    cd sockshop-deploy
	    if git status --porcelain | wc -l | grep -v -q '0'; then
	       git add --all && git commit -m 'Update Staging carts image version to ${env.REPOSITORY}:${env.TAG_STAGING}'
	       git push origin master
	    fi
	    """
            //sh "cd sockshop-deploy/ && git add --all && git commit -m 'Update Staging carts image version to ${env.REPOSITORY}:${env.TAG_STAGING}'"
            //sh 'cd sockshop-deploy/ && git push origin master'
        }
    }

    stage('Mark and push artifact for production namespace') {
        when {
            expression {
		        //return env.BRANCH_NAME ==~ 'release/.*'
		        return env.BRANCH_NAME ==~ 'master'
            }
        }
        steps {
	        script {
                //now that passed tests, then tag the image just built and push with just the version tag
		        docker.withRegistry('https://registry.hub.docker.com', 'dockerhub') {
                    docker.image("${env.REPOSITORY}:${env.TAG_STAGING}").push("${env.TAG_PROD}")
                    }
                }
            }
        }


    stage('Check in Production deployment change') {
        when {
            expression {
                //return env.BRANCH_NAME ==~ 'release/.*'
                return env.BRANCH_NAME ==~ 'XXXmaster'
            }
        }
        steps {
            replaceImageName("${env.REPOSITORY}:${env.TAG_PROD}", "sockshop-deploy/prod/carts.yml")
	    sh """
	    cd sockshop-deploy
	    if git status --porcelain | wc -l | grep -v -q '0'; then
	       git add --all && git commit -m 'Update Staging carts image version to ${env.REPOSITORY}:${env.TAG_PROD}'
	       git push origin master
	    fi
	    """
            //sh "cd sockshop-deploy/ && git add --all && git commit -m 'Update Production carts image version to ${env.REPOSITORY}:${env.TAG_PROD}'"
            //sh "cd sockshop-deploy/ && git push origin master"
        }
    }

    stage('Deploy to prod namespace') {
        when {
            expression {
                //return env.BRANCH_NAME ==~ 'release/.*'
                return env.BRANCH_NAME ==~ 'master'
            }
        }
        steps {
            script {
                def deploy_cmd = './sockshop-utils/dynatrace-scripts/pushdeployment.sh SERVICE CONTEXTLESS ' + DT_SERVICE_TAGNAME + ' ' + DT_SERVICE_TAGVALUE +
                        ' ${BUILD_TAG} ${BUILD_NUMBER} ${JOB_NAME} ${JENKINS_URL}' +
                        ' ${JOB_URL} ${BUILD_URL} ${GIT_COMMIT}'
                echo deploy_cmd
                sh deploy_cmd

                // Jenkins Credentials need to be configured with gcloud credentials
                withCredentials([file(credentialsId: 'GC_KEY', variable: 'GC_KEY')]) {
                    sh "gcloud auth activate-service-account --key-file=${GC_KEY}"
                    sh "gcloud container clusters get-credentials ${GC_CLUSTER} --zone ${GC_ZONE} --project ${GC_PROJECT}"
                    sh "./sockshop-utils/create_namespace.sh prod"
                    sh "kubectl apply -f sockshop-deploy/prod/carts.yml"
                    sh "kubectl apply -f sockshop-deploy/prod/carts-svc.yml"

                    echo "waiting for the service to start..."
                    sleep 180
                    sh "kubectl -n prod get pods -o wide"
		    sh "kubectl -n prod get service -o wide"
                }
            }
        }
    }
  }
}
