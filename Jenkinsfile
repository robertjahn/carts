def fileValueSubstitute(find_this, replace_with, file) {
   sh "sed -i.bak \"s|$find_this|$replace_with|g\" $file"
}

pipeline {
  agent any
  environment {
    APP_NAME = "cart"
    VERSION = readFile 'version'
    ARTIFACT_ID = "${env.APP_NAME}"

    DT_SERVICE_TAGNAME = "ServiceName"
    DT_SERVICE_TAGVALUE = "microservices-demo-front-end"

    //DockerHub public requires format of <account>/<repo>:<tag>
    //and does not support multiple forward slashes in the name, so must alter format
    REPOSITORY = "robjahn/${env.ARTIFACT_ID}"
    TAG_DEV = "${env.VERSION}-SNAPHOT-${env.BUILD_NUMBER}"
    TAG_STAGING = "${env.VERSION}"
	  
    // hardcoded for now within Jenkins Global Properties since dont have a DNS.  
    // can later adjust logic use kubectl get service as an approach
    SERVICE_URL = "${CART_SERVICE_IP}"	   
	  
    // also need to figure out how to update or use DNS for the mongo DB defined here:
    // https://github.com/robertjahn/carts/blob/master/src/main/resources/application.properties  
    //	  spring.data.mongodb.uri=mongodb://10.55.247.203/data
	  	  
    //These are the original DT project values:
    //string(name: 'SERVER_URL', value: "${env.APP_NAME}.dev"),	  
    //ARTIFACT_ID = "sockshop/" + "${env.APP_NAME}"
    //TAG = "${env.DOCKER_REGISTRY_URL}:5000/library/${env.ARTIFACT_ID}"
    //TAG_DEV = "${env.TAG}-${env.VERSION}-${env.BUILD_NUMBER}"
    //TAG_STAGING = "${env.TAG}-${env.VERSION}"
  }	

  stages {
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
    stage('Maven Build') {
      steps {
	echo "Building branch_name: ${env.BRANCH_NAME}"
        sh 'mvn -B clean package'
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
            image = docker.build("${env.REPOSITORY}:${env.TAG_DEV}")
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

        def deploy_cmd = './sockshop-utils/dynatrace-scripts/pushdeployment.sh SERVICE CONTEXTLESS ' + DT_SERVICE_TAGNAME + ' ' + DT_SERVICE_TAGVALUE +
           ' ${BUILD_TAG} ${BUILD_NUMBER} ${JOB_NAME} ${JENKINS_URL}' +
           ' ${JOB_URL} ${BUILD_URL} ${GIT_COMMIT}'
        echo deploy_cmd
        sh deploy_cmd

	    fileValueSubstitute("replace-the-image-name", "${env.REPOSITORY}:${env.TAG_DEV}", "sockshop-deploy/stage/carts.yml")

        // Jenkins Credentials need to be configured with gcloud credentials
        withCredentials([file(credentialsId: 'GC_KEY', variable: 'GC_KEY')]) {
            sh("gcloud auth activate-service-account --key-file=${GC_KEY}")
            sh("gcloud container clusters get-credentials gke-demo --zone us-east1-b --project jjahn-demo-1")
	        sh("gcloud compute instances list")
	        sh '''
		       if [[ $(kubectl get namespace stage | wc -l) -eq 0 ]]; then
			     echo "Create namespace stage..."
			     kubectl create namespace stage
		       fi
	        '''
  	        sh("kubectl apply -f sockshop-deploy/stage/carts.yml")
  	        sh("kubectl apply -f sockshop-deploy/stage/carts-svc.yml")
            sleep 10
	        sh("kubectl get pods -n stage")
	    }
      }
    }
    stage('Run health check in dev') {
      when {
        expression {
          return env.BRANCH_NAME ==~ 'release/.*' || env.BRANCH_NAME ==~'XXXmaster'
        }
      }
      steps {
        echo "waiting for the service to start..."
        sleep 180

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
	  
    stage('Run functional check in dev') {
      when {
        expression {
          return env.BRANCH_NAME ==~ 'release/.*' || env.BRANCH_NAME ==~'XXXmaster'
        }
      }
      steps {

        def start_test_cmd = './sockshop-utils/pushevent.sh SERVICE CONTEXTLESS '+ DT_SERVICE_TAGNAME + ' ' + DT_SERVICE_TAGVALUE +
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

        def end_test_cmd = './sockshop-utils/pushevent.sh SERVICE CONTEXTLESS '+ DT_SERVICE_TAGNAME + ' ' + DT_SERVICE_TAGVALUE +
          ' "ENDING Load Test as part of Job: " ${JOB_NAME} Jenkins-End-Test ' +
          ' ${JOB_URL} ${BUILD_URL} ${GIT_COMMIT}'
        echo end_test_cmd
        sh end_test_cmd
      }
    }
	  
    stage('Mark artifact for staging namespace') {
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
                    docker.image("${env.REPOSITORY}:${env.TAG_DEV}").push("${env.TAG_STAGING}")
                }    
            }
        }
    }
	  
    //stage('Deploy to staging') {
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
