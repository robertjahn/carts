def fileValueSubstitute(find_this, replace_with, file) {
   sh "sed -i.bak \"s|$find_this|$replace_with|g\" $file"
}

pipeline {
  agent any
  environment {

    // Assumes Jenkins Environment Variables set for GC_PROJECT, GC_CLUSTER, GC_ZONE, CART_SERVICE_IP

    APP_NAME = "cart"
    VERSION = readFile 'version'
    ARTIFACT_ID = "${env.APP_NAME}"

    DT_SERVICE_TAGNAME = "ServiceName"
    DT_SERVICE_TAGVALUE = "carts"

    //DockerHub public requires format of <account>/<repo>:<tag>
    //and does not support multiple forward slashes in the name, so must alter format
    REPOSITORY = "robjahn/${env.ARTIFACT_ID}"
    TAG_STAGING = "${env.VERSION}-SNAPHOT-${env.BUILD_NUMBER}"
    TAG_PROD = "${env.VERSION}"
	  
    // hardcoded for now within Jenkins Global Properties since dont have a DNS.  
    // can later adjust logic use kubectl get service as an approach
    SERVICE_URL = "${CART_SERVICE_IP}"	   

  }	

  stages {

    stage('Maven Build') {
      steps {
	echo "Building branch_name: ${env.BRANCH_NAME}"
        //sh 'mvn -B clean package -DskipTests'
      }
    }

    stage('Checkout') {
      steps {
        // into a deployment subdirectory we checkout the kubectl deployment scripts
        dir ('sockshop-deploy') {
          git url: 'https://github.com/robertjahn/sockshop-deploy.git', branch: 'master'
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
          return env.BRANCH_NAME ==~ 'release/.*' || env.BRANCH_NAME ==~'XXXmaster'
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
    stage('Deploy to staging namespace') {
      when {
        expression {
          return env.BRANCH_NAME ==~ 'release/.*' || env.BRANCH_NAME ==~'XXXmaster'
        }
      }
      steps {
        script {
          def deploy_cmd = './sockshop-utils/dynatrace-scripts/pushdeployment.sh SERVICE CONTEXTLESS ' + DT_SERVICE_TAGNAME + ' ' + DT_SERVICE_TAGVALUE +
            ' ${BUILD_TAG} ${BUILD_NUMBER} ${JOB_NAME} ${JENKINS_URL}' +
            ' ${JOB_URL} ${BUILD_URL} ${GIT_COMMIT}'
          echo deploy_cmd
          sh deploy_cmd

          fileValueSubstitute("replace-the-image-name", "${env.REPOSITORY}:${env.TAG_STAGING}", "sockshop-deploy/staging/carts.yml")

          // Jenkins Credentials need to be configured with gcloud credentials
          withCredentials([file(credentialsId: 'GC_KEY', variable: 'GC_KEY')]) {
            sh "gcloud auth activate-service-account --key-file=${GC_KEY}"
            sh "gcloud container clusters get-credentials ${GC_CLUSTER} --zone ${GC_ZONE} --project ${GC_PROJECT}"
            sh ./sockshop-utils/create_namespace.sh staging
            sh kubectl apply -f sockshop-deploy/staging/carts.yml
            sh kubectl apply -f sockshop-deploy/staging/carts-svc.yml

            echo "waiting for the service to start..."
            sleep 180
            sh kubectl get pods -n staging
          }
        }
      }
    }
    stage('Run health check in staging') {
      when {
        expression {
          return env.BRANCH_NAME ==~ 'release/.*' || env.BRANCH_NAME ==~'XXXmaster'
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
	  
    stage('Run functional check in staging') {
      when {
        expression {
          return env.BRANCH_NAME ==~ 'release/.*' || env.BRANCH_NAME ==~'XXXmaster'
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

    stage('Check in Staging deployment change') {
        when {
            expression {
                return env.BRANCH_NAME ==~ 'release/.*' || env.BRANCH_NAME ==~'master'
            }
        }
        steps {
            fileValueSubstitute("replace-the-image-name", "${env.REPOSITORY}:${env.TAG_STAGING}", "sockshop-deploy/staging/carts.yml")

            withCredentials([usernamePassword(credentialsId: 'github', passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
                //sh "git config --global user.email ${env.GIT_USER_EMAIL}"
                //sh "git clone https://${GIT_USERNAME}:${GIT_PASSWORD}@github.com/dynatrace-sockshop/k8s-deploy-staging"
                sh "cd sockshop-deploy/ && git add --all && git commit -m 'Update carts image version to ${env.REPOSITORY}:${env.TAG_STAGING}'"
                sh 'cd sockshop-deploy/ && git push https://${GIT_USERNAME}:${GIT_PASSWORD}@github.com/dynatrace-sockshop/k8s-deploy-staging'
            }
        }
    }

    stage('Mark artifact for production namespace') {
        when {
            expression {
		        //return env.BRANCH_NAME ==~ 'release/.*'
		        return env.BRANCH_NAME ==~ 'XXXmaster'
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
	  
    //stage('Deploy to production') {
      //when {
      //  beforeAgent true
      //  expression {
      //    return env.BRANCH_NAME ==~ 'release/.*'
      //  }
      //}
      //agent {
      //  label 'git'
      //}
      //steps {
        //withCredentials([usernamePassword(credentialsId: 'git-credentials-acm', passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
        //    sh "git config --global user.email ${env.GIT_USER_EMAIL}"
        //    sh "git clone https://${GIT_USERNAME}:${GIT_PASSWORD}@github.com/dynatrace-sockshop/k8s-deploy-staging"
        //    sh "cd k8s-deploy-staging/ && sed -i 's#image: .*#image: ${env.TAG_STAGING}#' carts.yml"
        //    sh "cd k8s-deploy-staging/ && git add carts.yml && git commit -m 'Update carts version ${env.VERSION}'"
        //    sh 'cd k8s-deploy-staging/ && git push https://${GIT_USERNAME}:${GIT_PASSWORD}@github.com/dynatrace-sockshop/k8s-deploy-staging'
        //}
      //}
    }
}
