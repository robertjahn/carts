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

    DT_SERVICE_TAGNAME = "Sockshop"
    DT_SERVICE_TAGVALUE = ""

    //DockerHub public requires format of <account>/<repo>:<tag>
    //and does not support multiple forward slashes in the name, so must alter format
    REPOSITORY = "robjahn/${env.ARTIFACT_ID}"
    TAG_STAGING = "${env.VERSION}-SNAPHOT-${env.BUILD_NUMBER}"
    TAG_PROD = "${env.VERSION}"
	  
    // hardcoded for now 
    // can later adjust logic use kubectl get service as an approach
    SERVICE_URL_STAGING = "35.243.193.99"
    SERVICE_URL_PROD = "35.237.140.248"
	  
    // Namespaces
    NAMESPACE_PROD = "prod"
    NAMESPACE_STAGING = "stage"
  }	

  stages {

    stage('Build') {
      steps {
          echo "*************************************************************"
          echo "REPOSITORY = ${env.REPOSITORY}"
          echo "TAG_STAGING = ${env.TAG_STAGING}"
          echo "TAG_PROD = ${env.TAG_PROD}"
          echo "SERVICE_URL_STAGING = ${SERVICE_URL_STAGING}"
          echo "SERVICE_URL_PROD = ${SERVICE_URL_PROD}"
	  echo "*************************************************************"    

          echo "Building branch_name: ${env.BRANCH_NAME}"
          sh "mvn -B clean package -DskipTests"
      }
    }

    stage('Checkout') {
      steps {
        // into a deployment subdirectory we checkout the kubectl deployment scripts
        // we need to commit back so checkout with credentials
        dir ('sockshop-deploy') {
          withCredentials([usernamePassword(credentialsId: 'github', passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
            git url: "https://${GIT_USERNAME}:${GIT_PASSWORD}@github.com/robertjahn/sockshop-deploy.git", branch: "${env.BRANCH_NAME}"
	      }
        }

        // into a deployment subdirectory we copy the utility scripts
        dir ('sockshop-utils') {
          git url: 'https://github.com/robertjahn/sockshop-utils.git', branch: 'master'
        }

        // into a dynatrace-cli subdirectory we copy the Dynatrace CLI
        dir ('dynatrace-cli') {
          git url: 'https://github.com/Dynatrace/dynatrace-cli.git', branch: 'master'
        }
      }
    }
	  
    stage('Docker build and push to registry'){
      steps {
        script {
            def image
	    switch (env.BRANCH_NAME) {
              case 'master':
		DT_SERVICE_TAGVALUE = "stage-carts"
                echo "Buiding Staging Docker image"
	        image = docker.build("${env.REPOSITORY}:${env.TAG_STAGING}")
                break
              case 'release':
		DT_SERVICE_TAGVALUE = "prod-carts"
	        echo "Buiding Production Docker image"
	        image = docker.build("${env.REPOSITORY}:${env.TAG_PROD}")
                break
              default: 
	        error "Only support master and release branches"
              } 	
            docker.withRegistry('https://registry.hub.docker.com', 'dockerhub') {
                image.push()
            }
	}
      }
    }
    stage('Deploy') {
      steps {
        script {
	  def tag
	  def subdirectory
	  def namespace	
          if ("${env.BRANCH_NAME}" == "release") {
	     tag = "${env.TAG_PROD}"
             subdirectory = "prod"
	     namespace = "${env.NAMESPACE_PROD}"
	     echo "Deploying version ${tag} to Production"
	  } else {
	     tag = "${env.TAG_STAGING}"
             subdirectory = "staging"
	     namespace = "${env.NAMESPACE_STAGING}"
	     echo "Deploying version ${tag} to Staging"
	  }
          replaceImageName("${env.REPOSITORY}:${tag}", "sockshop-deploy/${subdirectory}/carts.yml")

          // Jenkins Credentials need to be configured with gcloud credentials
          withCredentials([file(credentialsId: 'GC_KEY', variable: 'GC_KEY')]) {
	    // get the required cluster creds
            sh "gcloud auth activate-service-account --key-file=${GC_KEY}"
            sh "gcloud container clusters get-credentials ${GC_CLUSTER} --zone ${GC_ZONE} --project ${GC_PROJECT}"
	
	    // create namespace it its not there	  
            sh "./sockshop-utils/create_namespace.sh ${namespace}"
		 
            // do the deployment
            //sh "kubectl apply -f sockshop-deploy/${subdirectory}/carts.yml"
            //sh "kubectl apply -f sockshop-deploy/${subdirectory}/carts-svc.yml"

            echo "waiting for the service to start..."
            //sleep 180
            sh "kubectl -n ${namespace} get pods -o wide"
            sh "kubectl -n ${namespace} get service -o wide"		  
          }
		
	  // push the dynatrace deployment event
	  // ENTITYTYPE TAGCONTEXT TAGNAME TAGVALUE 
	  // DEPLOYMENTNAME DEPLOYMENTVERSION DEPLOYMENTPROJECT SOURCE CILINK 
          // JENKINSURL GITBRANCH GITCOMMIT
	  // https://wiki.jenkins.io/display/JENKINS/Building+a+software+project	
          def deploy_cmd = './sockshop-utils/dynatrace-scripts/pushdeployment.sh ' +
	    ' SERVICE CONTEXTLESS ' + DT_SERVICE_TAGNAME + ' ' + DT_SERVICE_TAGVALUE +
	    ' ${BUILD_TAG} ' + tag + ' sock-shop ${GIT_URL} none ' +
	    ' ${BUILD_URL} ${GIT_BRANCH} ${GIT_COMMIT}'
          echo deploy_cmd
          sh deploy_cmd
        }
      }
    }
	  
    stage('Check in deployment change') {
      steps {
	script {	
          if ("${env.BRANCH_NAME}" == "release") {
	     tag = "${env.TAG_PROD}"
	     subdirectory = "prod"
	     echo "Check in production image name change to ${tag}"
	  } else {
	     tag = "${env.TAG_STAGING}"
	     subdirectory = "staging"
	     echo "Check in staging image name change to ${tag}"
	  }
	  replaceImageName("${env.REPOSITORY}:${tag}", "sockshop-deploy/${subdirectory}/carts.yml")
	  sh """
	    cd sockshop-deploy
	    if git status --porcelain | wc -l | grep -v -q '0'; then
	       git add --all && git commit -m 'Update ${env.BRANCH_NAME} carts image version to ${env.REPOSITORY}:${tag}'
	       git push origin ${env.BRANCH_NAME}
	  fi
	  """
        }
      }
    }
	  
    stage('Run health check') {
      steps {
        script {
	  def url
          if ("${env.BRANCH_NAME}" == "release") {
	     url = "${env.SERVICE_URL_PROD}"
	     echo "Running Production Health check against ${url}"
	  } else {
	     url = "${env.SERVICE_URL_STAGING}"
	     echo "Running Staging Health check against ${url}"
	  }
          build job: "acm-workshop/jmeter-as-container",
            parameters: [
              string(name: 'BUILD_JMETER', value: 'no'),
              string(name: 'SCRIPT_NAME', value: 'basiccheck.jmx'),
              string(name: 'SERVER_URL', value: "${url}"),
              string(name: 'SERVER_PORT', value: '80'),
              string(name: 'CHECK_PATH', value: '/health'),
              string(name: 'VUCount', value: '1'),
              string(name: 'LoopCount', value: '1'),
              string(name: 'DT_LTN', value: "HealthCheck_${BUILD_NUMBER}"),
              string(name: 'FUNC_VALIDATION', value: 'yes'),
              string(name: 'AVG_RT_VALIDATION', value: '0'),
              string(name: 'RETRY_ON_ERROR', value: 'yes')
            ]      
          //error("Force fail for testing")
	}
      }
    }
	  
    stage('Run load test') {
      steps {
        script {
	  def url
          if ("${env.BRANCH_NAME}" == "release") {
	     url = "${env.SERVICE_URL_PROD}"
	     echo "Running Production Health check against ${url}"
	  } else {
	     url = "${env.SERVICE_URL_STAGING}"
	     echo "Running Staging Health check against ${url}"
	  }
          def start_test_cmd = './sockshop-utils/dynatrace-scripts/pushevent.sh SERVICE CONTEXTLESS '+ DT_SERVICE_TAGNAME + ' ' + DT_SERVICE_TAGVALUE +
            ' "STARTING Load Test as part of Job: " ${JOB_NAME} Jenkins-Start-Test ' +
            ' ${JOB_URL} ${BUILD_URL} ${GIT_COMMIT}'
          echo start_test_cmd
          sh start_test_cmd

          build job: "acm-workshop/jmeter-as-container",
            parameters: [
              string(name: 'BUILD_JMETER', value: 'no'),
              string(name: 'SCRIPT_NAME', value: "${env.APP_NAME}_load.jmx"),
              string(name: 'SERVER_URL', value: "${url}"),
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
  }
}
