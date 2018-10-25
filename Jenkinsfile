def fileValueSubstitute(placeholder, value, file) {
   sh "sed -i.bak s/:\\\${$placeholder}/:$value/g $file"
}

pipeline {
  agent any
  environment {
    APP_NAME = "cart"
    VERSION = readFile 'version'
    ARTIFACT_ID = "${env.APP_NAME}"

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
    stage('Deploy to dev namespace') {
      when {
        expression {
          return env.BRANCH_NAME ==~ 'release/.*' || env.BRANCH_NAME ==~'master'
        }
      }
      steps {
        //sh "sed -i 's#image: .*#image: ${env.TAG}:${env.TAG_DEV}#' manifest/carts.yml"
	fileValueSubstitute("to-be-replaced-by-jenkins-hehe", "${env.TAG}:${env.TAG_DEV}", "manifest/carts.yml")      
        withCredentials([file(credentialsId: 'GC_KEY', variable: 'GC_KEY')]) {
            sh("gcloud auth activate-service-account --key-file=${GC_KEY}")
            sh("gcloud container clusters get-credentials gke-demo --zone us-east1-b --project jjahn-demo-1")
	    sh("gcloud compute instances list")
	    sh '''
		if [[ $(kubectl get namespace sock-shop | wc -l) -eq 0 ]]; then
			echo "Create namespace dev..."
			kubectl create namespace dev
		fi
	    '''
  	    //sh("kubectl -n dev apply -f manifest/carts.yml")
	    sh("kubectl get pods -n dev")
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
