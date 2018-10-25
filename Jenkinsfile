pipeline {
  agent any
  environment {
    APP_NAME = "cart"
    VERSION = readFile 'version'
    ARTIFACT_ID = "${env.APP_NAME}"

    //DockerHub public requires format of <account>/<repo>
    // so must alter format
    TAG = "robjahn/${env.ARTIFACT_ID}"
    TAG_DEV = "${env.VERSION}-${env.BUILD_NUMBER}"
    TAG_STAGING = "${env.VERSION}"
	  
    // hardcoded for now within Jenkins Global Properties since dont have a DNS.  
    // can later adjust logic use kubectl get service as an approach
    // DT project uses this //string(name: 'SERVER_URL', value: "${env.APP_NAME}.dev"),	  
    SERVICE_URL = ${CART_SERVICE_IP}	   
	  	  
    // These are DT project values	  
    //ARTIFACT_ID = "sockshop/" + "${env.APP_NAME}"
    //TAG = "${env.DOCKER_REGISTRY_URL}:5000/library/${env.ARTIFACT_ID}"
    //TAG_DEV = "${env.TAG}-${env.VERSION}-${env.BUILD_NUMBER}"
    //TAG_STAGING = "${env.TAG}-${env.VERSION}"
  }
  stages {
    stage('Maven build') {
      steps {
	echo "Building branch_name: ${env.BRANCH_NAME}"
        checkout scm
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
            def app
            app = docker.build("${env.TAG}")
            docker.withRegistry('https://registry.hub.docker.com', 'dockerhub') {
                app.push("${env.TAG_DEV}")
            }
        }
      }
    }
    stage('Deploy to dev namespace') {
      when {
        expression {
          return env.BRANCH_NAME ==~ 'release/.*' || env.BRANCH_NAME ==~'XXXmaster'
        }
      }
      steps {
        sh "sed -i 's#image: .*#image: ${env.TAG}:${env.TAG_DEV}#' manifest/carts.yml"
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
  	    sh("kubectl -n dev apply -f manifest/carts.yml")
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
		return env.BRANCH_NAME ==~ 'master/.*'
            }
        }
        steps {
	    script {
                withDockerRegistry([ credentialsId: "dockerhub", url: "https://registry.hub.docker.com" ]) {
                    // following commands will be executed within logged docker registry
                    sh "docker tag ${env.TAG}:${env.TAG_DEV} ${env.TAG_STAGING}"
		    sh "docker push ${env.TAG}:${env.TAG_STAGING}"
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
