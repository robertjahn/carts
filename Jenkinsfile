pipeline {
  agent any
  //agent {
  //  label 'maven'
  //}
  environment {
    APP_NAME = "carts"
    VERSION = readFile 'version'
    //ARTIFACT_ID = "sockshop/" + "${env.APP_NAME}"
    ARTIFACT_ID = "${env.APP_NAME}"
    //TAG = "${env.DOCKER_REGISTRY_URL}:5000/library/${env.ARTIFACT_ID}"
    TAG = "robjahn/${env.ARTIFACT_ID}"
    TAG_DEV = "${env.TAG}-${env.VERSION}-${env.BUILD_NUMBER}"
    TAG_STAGING = "${env.TAG}-${env.VERSION}"
    SERVICE_URL = "35.231.79.243"	   
  }
  stages {
    stage('Maven build') {
      steps {
        checkout scm
        //container('maven') {
        sh 'mvn -B clean package'
        //}
      }
    }
    //stage('Docker build') {
    //  when {
    //    expression {
    //      return env.BRANCH_NAME ==~ 'release/.*' || env.BRANCH_NAME ==~'master'
    //    }
    //  }
    //  steps {
    //    //container('docker') {
    //      echo "branch_name=${env.BRANCH_NAME}"
    //      //sh "docker build -t ${env.TAG_DEV} ."
    //    //}
    //  }
    //}
    stage('Docker build and push to registry'){
      when {
        expression {
          return env.BRANCH_NAME ==~ 'release/.*' || env.BRANCH_NAME ==~'master'
        }
      }
      steps {
        script {
            echo "branch_name=${env.BRANCH_NAME}"
          
            def app
            app = docker.build("${env.TAG_DEV}")
            docker.withRegistry('https://registry.hub.docker.com', 'dockerhub') {
                //sh "docker build -t $DOCKER_REGISTRY/$APP_NAME ."
                //sh "docker push $DOCKER_REGISTRY/$APP_NAME"
                app.push("latest")
            }
        }
        //container('docker') {
        //  sh "docker push ${env.TAG_DEV}"
        //}
      }
    }
    stage('Deploy to dev namespace') {
      when {
        expression {
          return env.BRANCH_NAME ==~ 'release/.*' || env.BRANCH_NAME ==~'master'
        }
      }
      steps {
        //container('kubectl') {
        sh "sed -i 's#image: .*#image: ${env.TAG_DEV}#' manifest/carts.yml"
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
        
      //sh "kubectl -n dev apply -f manifest/carts.yml"
        //}
      }
    }
    stage('Run health check in dev') {
      when {
        expression {
          return env.BRANCH_NAME ==~ 'release/.*' || env.BRANCH_NAME ==~'master'
        }
      }
      steps {
        echo "waiting for the service to start..."
        sleep 180

	//string(name: 'SERVER_URL', value: "${env.APP_NAME}.dev"),
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
          return env.BRANCH_NAME ==~ 'release/.*' || env.BRANCH_NAME ==~'master'
        }
      }
      steps {
	//string(name: 'SERVER_URL', value: "${env.APP_NAME}.dev"),
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
    //stage('Mark artifact for staging namespace') {
    //  when {
    //    expression {
    //      return env.BRANCH_NAME ==~ 'release/.*'
    //    }
    //  }
    //  steps {
    //    container('docker'){
    //      sh "docker tag ${env.TAG_DEV} ${env.TAG_STAGING}"
    //      sh "docker push ${env.TAG_STAGING}"
    //    }
    //  }
    //}
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
