{ -> }
import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;
import groovy.time.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import javax.xml.bind.DatatypeConverter;


def checkoutGit(localPath, remotePath, branch) {
	checkout([
		$class: 'GitSCM',
		branches: [[name: branch]],
		doGenerateSubmoduleConfigurations: false,
		extensions: [[
			$class: 'RelativeTargetDirectory',
			relativeTargetDir: localPath
		]],
		submoduleCfg: [],
		userRemoteConfigs: [[
				credentialsId: env.CREDENTIAL_ID_GIT,
				url: remotePath
		]]
	])
}

// быстрый checkout 1 папки из git без истории изменений
def checkoutGitFolder(folders, branch, repo, reference_dir=null, target_dir=null, gitLsfPull=false) {

	checkHead(branch)

    extensions = []
    def repoName = repo.split('/')[-1]
    if (reference_dir == null || reference_dir) {
        if (reference_dir==null) {
            reference_dir = "/home/jenkins/git_cache/"
        }
        reference_dir = "${reference_dir}${repoName}"
	}
    
    extensions.add([$class: 'CloneOption', depth: 1, noTags: true, honorRefspec: true, reference: reference_dir, shallow: true])
    extensions.add([$class: 'PruneStaleBranch'])

    if (folders) {
        def foldersList = []
	    folders.each {v -> foldersList.add([path: v])}
        extensions.add([$class: 'SparseCheckoutPaths', sparseCheckoutPaths: foldersList])
    }
	
    if (target_dir) {
        extensions.add([$class: 'RelativeTargetDirectory', relativeTargetDir: target_dir])
    }
	if (gitLsfPull) {
		extensions.add([$class: 'GitLFSPull'])
	}
	
	checkout changelog: false, poll: false, scm: [
		$class: 'GitSCM', 
		branches: [[name: branch]], 
		extensions: extensions, 
		userRemoteConfigs: [[credentialsId: env.CREDENTIAL_ID_GIT, url: repo]]
	]	
}

// быстрый проброс статуса сборки в jenkins
def save_build_result_to_jc(status) {
	def jcAddress = 'http://jenkins-control.tensor.ru'
	def json = JsonOutput.toJson([job_url: env.JOB_URL, build: env.BUILD_ID, status: status])
	println json
	httpRequest acceptType: 'APPLICATION_JSON_UTF8', consoleLogResponseBody: true, contentType: 'APPLICATION_JSON_UTF8',
				httpMode: 'POST', requestBody: json, responseHandle: 'NONE', timeout: 0, validResponseCodes: '100:502',
				url: "${jcAddress}/api/test_result/save_build_status"
}

// получение статуса сборки по stdout runner.py
def getResultBuild(exitCode) {
	switch (exitCode) {
	    case 0:
            result = "SUCCESS";
            break;
        case 1:
            result = "UNSTABLE";
            break;
        default:  // status 2
            result = "FAILURE";
            break;
	}
	return result
}

// получение result.db из артефактов предыдущего билда
def getResultDB() {
	def prevBuild = env.BUILD_ID.toInteger() - 1
	url = "${env.JOB_URL}${prevBuild}/artifact/result.db"
	script = """
	if [ `curl -s -w "%{http_code}" --compressed -o tmp_result.db "${url}"` = "200" ]; then
		echo "result.db exitsts"; cp -fr tmp_result.db result.db
		else rm -f result.db
	fi
	"""
	sh returnStdout: true, script: script
}

// вычисляем версию из вехи
@NonCPS
def extractVersion(milestoneVar) {
  def pattern = ~/\d+.\d+.\d+.?\d*/
  def findVersion = (milestoneVar =~ /$pattern/)
  assert findVersion.size() > 0, 'Не смогли вычислить версию тестов в переданной вехе'
  return findVersion[0].trim()
}

// парсим имя сборки, чтобы получить стенд, продукт и юнит
def parseNameJob(name) {
	def nameJob = [:]
	def pattern = ~/(?<stand>fix|test|pre-test|dev|pre-dev)?-?(?<product>[a-z_]+)-?(?<unit>\S*)?/
	
	def findV = (name =~ /$pattern/)
	if (findV.matches()) {		
		for (i in ['stand', 'product', 'unit']) {			
			tmp = findV.group(i)			
			if (tmp) {
				nameJob.put(i, tmp)
			}
		}
	}	
	return nameJob
}

// универсальное получение имени настроек
def getSettingName(productName) {
	def tmp, jobName
	
	// удаляем папку из имени
	if (env.JOB_NAME.contains("/")) {
		jobName = env.JOB_NAME.split("/")[1]

	} else {
		jobName = env.JOB_NAME
	}

	// вычисляем stand + product + unit
	if (!jobName.startsWith('run')) {
		tmp = jobName.split(' ')[1]
	} else {
		tmp = jobName.split(' ')[2]
	}	
	
	def result = parseNameJob(tmp)
	def settingsName = ""

	if (productName != "ATF") {
		settingsName = "VER_"		
	} else {
		settingsName = "ATF_"
		productName = result.get("product").toUpperCase()
	}	

	// у prod нет стендов
	def stand = result.get('stand', null)	
	if (stand) {		
		// у основных сервисов нет стендов на dev, pre-dev. 
		if (stand == "pre-dev" && ["ONLINE", "INSIDE", "REG"].contains(productName)) {
			stand = "dev"
		}
		settingsName += "${stand.toUpperCase()}_"
	}

	settingsName += productName.toUpperCase()
	
	// только на online есть юниты
	def unit = result.get("unit", null)
	if (productName == "ONLINE" && unit) {
		settingsName += "_${unit.toUpperCase()}"
	}
	if (productName == "INSIDE" && unit) {
		settingsName += "_${unit.toUpperCase()}"
	}

	settingsName = settingsName.replace("-", "_")
	return settingsName
}

def getProductPages(product) {
	product = product.toLowerCase()
	if (product in ['online', 'my']) {
		product = 'inside'
	} else if (product == 'sbis_ru') {
		product = 'sbis'
	}
	return product
}

// получение имени мапнутого диска в зависимости от билдера
def getDiskName() {
	def buildersMap = [
		"test-selenium-builder1": "W",
		"test-selenium-builder2": "T",
		"test-selenium-builder3": "Y",
		"test-selenium-builder4": "V",
		"test-selenium-builder5": "R",
		"test-selenium-builder7": "U",
		"test-selenium-builder8": "X",
		"psdr-prognix7": "S",
		"test-selenium-builder9": "P",
		"test-selenium-builder10": "O",
	]	
    def disk = buildersMap.get(env.NODE_NAME, null)    
    // assert disk : "Unknown builder: ${env.NODE_NAME}"        
	return disk
}

// получение пути до файла с настройками
def getSettingsPath(props_path=null) {
	if (props_path!=null){
		settingsPath = props_path
	}
	else if (env.NODE_NAME != "test-selenium-builder1") {
		settingsPath = '/home/jenkins/shared_builder1/settings.props'		
	} else {
		settingsPath = '/home/jenkins/tests/settings.props'
	}
	return settingsPath
}

def getDependancy(product, operator_dependency) {
	// сюда можно добавлять новые продукты, показывает какие PO нужны
    def dependancyMap = [
        "ONLINE": ["ATF", "REG", "INSIDE", "CLOUD", "SBIS", "FED", "SABYTRADE", "SABYGET", "TENSOR"],
        "MY": ["ATF", "REG", "INSIDE", "CLOUD", "FED", "SABYGET"],
        "INSIDE": ["ATF", "REG", "INSIDE", "CLOUD", "SBIS", "FED", "SABYTRADE", "SABYGET", "TENSOR"],
        "REG": ["ATF", "REG", "INSIDE", "CLOUD", "SBIS", "FED", "SABYGET"],
        "CLOUD": ["ATF", "INSIDE", "CLOUD", "REG", "ADMIN", "FED", "SABYGET"],
		"ADMIN": ["ATF", "ADMIN", "CLOUD", "INSIDE", "REG", "FED", "SABYGET"],
		"SBIS": ["ATF", "SBIS", "REG", "CLOUD", "INSIDE", "TENSOR", "FED", "SABYGET"],
		"SABYGET": ["ATF", "SABYGET", "CLOUD", "INSIDE", "FED", "REG"],
		"SABYTRADE": ["ATF", "SABYTRADE", "SABYGET", "CLOUD", "INSIDE", "FED", "REG"],
		"TENSOR": ["ATF", "REG", "INSIDE", "CLOUD", "FED", "TENSOR", "SBIS", "SABYGET"],
		"WI": ["ATF", "WI", "CLOUD", "INSIDE", "FED"],
		"HELP_SBIS": ["ATF", "HELP_SBIS", "SBIS", "CLOUD", "INSIDE", "FED"],
		"SABYGET_PROMO": ["ATF", "SABYGET_PROMO"]
    ]

    // получаем зависимости, если продукт не найден, падаем
    // def dependancy = dependancyMap.get(product, null)
    // assert dependancy : "Unknown product: ${product}"
    // if (operator_dependency) {
    //     dependancy.addAll(["OTF", "CTT", "OFD" , "OFDTOOLS"])
	// }
	// return dependancy
}

// получение модификатора тестов (cloud, inside-only, xp, ie)
def getModificator() {
	def tmpName
	// получаем имя сборки без папки
    if (env.JOB_NAME.contains('/')) {
        tmpName = env.JOB_NAME.split('/')[1]
    } else {
        tmpName = env.JOB_NAME
    }
	def testType = tmpName.split(' ')[0].toLowerCase()
	if (testType.contains('api')) {
		return 'api'
	}
	tmpName = testType.replace(')', "")
	tmpName = tmpName.split('-')
	if (tmpName.size() > 1) {
		return tmpName[1]
	} else {
		return ""
	}	
}

def getListModificators(){
	def tmpName
	def result = []
	// получаем имя сборки без папки
	if (env.JOB_NAME.contains('/')) {
		tmpName = env.JOB_NAME.split('/')[1]
	} else {
		tmpName = env.JOB_NAME
	}
	def testType = tmpName.split(' ')[0].toLowerCase()
	tmpName = testType.replace(')', "")
	tmpName = tmpName.split('-')
	if (tmpName.size() > 1) {
		result = tmpName[1..tmpName.length-1]
	}
	return result
}

def saveVersions(quota) {
	//нет версии для демо, твин, приравняем к экст	
	if (quota=='fix_online_demo_ext'){
		quota = "fix_online_ext1"
	} else if (quota=='test_online_demo_ext') {
		quota = "test_online_ext"
	} else if (quota=='dev_admin_twin') {
		quota = "dev_admin"
	} else if (quota=='dev_online') {
		quota = "pre_test_inside"
    }	
	
	println("saveVersions key: ${quota}")
	def key = "VER_${quota}"
	key = key.toUpperCase()
	def url = "http://test-selenium-builder1:4444/api?key=${key}"
    httpRequest consoleLogResponseBody: false, outputFile: 'version.json', responseHandle: 'NONE', timeout: 5, url: url, validResponseCodes: '200:404'    
    archiveArtifacts allowEmptyArchive: true, artifacts: 'version.json'
	fileExists 'version.json'
	def props = readJSON  file: 'version.json'
	if ( !props['build'] ) {
		error("Не удалось получить значение для параметра VERSION")
	}
	return props
}

// возвращает словарь с настройками
def getSettingsProps(props_path=null) {
	def settingsPath = getSettingsPath(props_path)  // путь до файла с настройками
    fileExists settingsPath
    def props = readProperties file: settingsPath
	return props
}

// получаем настройку MILESTONE
// def getMilestoneSettings(stand, product, unit, props_path=null) {
// 	def props = getSettingsProps(props_path)
// 	def setName
// 	if ( stand ) {
// 		setName = "MILESTONE_${stand}_${product}"
// 	} else {
// 		setName = "MILESTONE_${product}"
// 	}
	
// 	if ( unit ) {
// 		setName += "_${unit}"
// 	}
// 	setName = setName.replace('-', '_').toUpperCase()
// 	def milestone = props[setName]
// 	if ( !milestone ) {
// 		error("Не удалось получить значение для параметра ${setName}")
// 	}
// 	return milestone
// }

def setStateStand(stand, block, jobName, units=null) {	
	def jcAddress = 'http://jenkins-control.tensor.ru'
	if (stand in ['fix', 'test', 'pre-test']) {
		body = [stand: stand, job_name: jobName, block: block];
		if (units != null && units instanceof List) {
			body['units'] = units
		}
		bodyStr = groovy.json.JsonOutput.toJson(body);
		httpRequest  url: "${jcAddress}/api/state/set", requestBody: bodyStr, 
					consoleLogResponseBody: true, contentType: 'APPLICATION_JSON_UTF8', httpMode: 'POST',  ignoreSslErrors: true, 
					responseHandle: 'NONE', validResponseCodes: '200:502'
    }
}


def updateEnvOnOneNode(settingsMap) {
	def scriptUpdate = load 'updateEnvironment.groovy'
	scriptUpdate.update(settingsMap)
}

def updateEnvOnAllNodes(settingsMap, settingsApiJob=null) {
	def update_jobs = [:];
	def settingList = [];
	def nodes = nodesByLabel 'acceptance'
	// для текущего билдера, чтобы еще один слот не занимать
	nodes -= env.NODE_NAME
	settingsMap.each{entry -> settingList.add(string(name: entry.key, value: entry.value))}
	// stash includes: '*.groovy', name: 'pipeline_lib'
	nodes.each { item ->		
		update_jobs["update env ${item}"] = {
			build job: "update_envoriment/${item}", parameters: settingList
			// node(item) {
			// 	unstash 'pipeline_lib'
			// 	def scriptUpdate = load 'updateEnvironment.groovy'
			// 	lock(item) {
			// 	// lock("${item}_updateEnv") {
			// 		scriptUpdate.update(settingsMap)
			// 	}
			// }
		}
	}
	
	// для текущего билдера, чтобы еще один слот не занимать
	update_jobs["update env ${env.NODE_NAME}"] = {
		updateEnvOnOneNode(settingsMap)
	}
	
	if (settingsApiJob){
		def api_nodes = nodesByLabel 'api_env'
		api_nodes.each { item ->
			update_jobs["update env ${item}"] = {
				build job: "update_environment_api/${item}", parameters: settingsApiJob
			}
		}
	}
	parallel update_jobs
}


def getAtfVersion(settingName, props) {
	/** Получить значение найстройки атф от общего к частному.
	 * ATF_FIX_ONLINE_EXT_AUTOTEST -> ATF_FIX -> ATF
	 * @param settingName - имя частной настройки
	 * @param props - словарь с настройки из файла
	 * @return имя текущей найстройки, значение */

	// получить индекст '_' , взять в обратном порядке
	def index_separator = settingName.findIndexValues{it == "_"}.reverse()
	if (props[settingName]) {
		return [props[settingName], settingName]
	}
	else {
		for ( i in index_separator) {
			def setting = settingName.substring(0, i.toInteger())
			if (props[setting]) {
				def value = props[setting]
				return [value, setting]
			}
		}
		throw new Exception("Не смогли получить настройку атф")
	}
}

// обновление окружений
def updateEnvorimentGit(product, update, all_nodes=false, featureBranch=false, updateApi=false, unit='', atf_ver=null, controlsVer=null, operator_dependency=false) {
	def settingName, value, settingAtf, settingAtfValue
	// TODO надо будет удалить _git
	def environmentPath = '/home/jenkins/environment_git'	
	product = product.toUpperCase()
	def dependancy = getDependancy(product, operator_dependency)  // получаем список зависимостей для продукта
	def productPages = getProductPages(product).toUpperCase()
	def repPaths = getRepPath(params)

	// считываем настройки из файла
    def props = getSettingsProps()

	// получаем версию, где лежат тесты
    def settingProduct = getSettingName(product)
    def ver = props[settingProduct]
	def settingsMap = [:];
	// для запуска обновления окружения на тачках API
	def settingsApiJob = []
    
    def pythonPathList = []
    // идем по зависимостям, генерим имена настроек и получаем их из файла
    for (item in dependancy) {
		if (item == "INSIDE" && product in ['ONLINE', 'MY']) {
			settingName = settingProduct
			value = ver
		}
		else {
            settingName = getSettingName(item)
			if (item == "ATF") {
				value_atf = getAtfVersion(settingName, props)
				settingName = value_atf[1]
				value = value_atf[0]
			}
			else {
				value = props[settingName]
				// Для юнитов совместимости брать FED_COMP
				if (item == "FED" && settingProduct in ["VER_FIX_ONLINE_EXT_AUTOTEST_OLD", "VER_FIX_ONLINE_EXT_VIP64", "VER_ONLINE_EXT4", "VER_ONLINE_EXT2"]) {
					println(">>>>>>> Юнит для тестирования совместимости")
					settingName = settingName + '_COMP'
					value = props[settingName]
				}
			}
        }
        if (item in repPaths.keySet()) {
		    value = repPaths[item]
		    settingName = getSettingName(item)
		}
		def itemLower = item.toLowerCase();
		if (item in ["OTF", "CTT", "OFDTOOLS"]) {  // Получаем версии библиотек для тестов ОФД
			pythonPathList.add("${environmentPath}/${itemLower}/${value}/")
			if (update) {
				settingsMap[item] = value;
			}
		}
		else if (item == "ATF") { // Получаем версию ATF
			if (atf_ver) {
				value = atf_ver
			}
			pythonPathList.add("${environmentPath}/atf/$value/")
			if (update) {
				settingsMap['ATF'] = value
				if (updateApi && product in ['ONLINE', 'INSIDE']) {
					settingsApiJob.add(string(name: item, value: value))
				}
			}
		}
		else if (productPages != item || !featureBranch) {  // Получаем версии пейджей
			pythonPathList.add("${environmentPath}/pages/${itemLower}/${value}/")
			if (update) {
				settingsMap[item] = value;
			}
			if (updateApi && item == 'INSIDE' && product in ['ONLINE', 'INSIDE']){
				settingsApiJob.add(string(name: 'API_FUNCTIONS', value: value))
			}
		}
        println("${settingName}: ${value}")
	}

	// добавляем пути на controls в PYTHONPATH
    if (!controlsVer) {
        def findVersion = ver =~ /.*(\d\d\.\d\d\d\d).*/
        сontrolsVersion = findVersion.matches() ? "rc-${findVersion.group(1)}" : "rc-99.00"
		// TODO удалить после выхода 7000 версии
		if  ('rc-23.1000' > сontrolsVersion && сontrolsVersion >= 'rc-22.7206') {
			сontrolsVersion = '22.7227/feature/revert-delay'
		}
    } else {
		сontrolsVersion = controlsVer
    }
    pythonPathList.add("${environmentPath}/controls/${сontrolsVersion}/")
	if (update) {
	    settingsMap['CONTROLS'] = сontrolsVersion
	}

	helpersBrach = 'master'
	if ("HELPERS" in repPaths.keySet()) {
	    helpersBrach = repPaths["HELPERS"]
    }
    settingsMap['HELPERS'] = helpersBrach
	pythonPathList.add("${environmentPath}/helpersFeature/${helpersBrach}")

	if (update){
		if (!all_nodes) {
			updateEnvOnOneNode(settingsMap)
			// build job: "update_envoriment/${env.NODE_NAME}", parameters: settingsJob
		} else {
			updateEnvOnAllNodes(settingsMap, settingsApiJob)
		}
	}

    // генерация путей к библиотекам
    def pythonPath = "export PYTHONPATH=" + pythonPathList.join(':')
	return [ver, pythonPath, props['SERVER_ADDRESS'], repPaths]
}

//Собираем параметры сборки по модификатору
def getBuildParams(modificator, standName){
	def jcAddress = 'http://jenkins-control.tensor.ru'
	def buildParams = [commandLine:[], userOptions: []]

	if (modificator.contains('cloud')) {
	    println("detected modificator 'cloud'")
		buildParams["commandLine"].add("-m \"not not_client_auth\"")
		buildParams["userOptions"].add("CLOUD_AUTH=True")
	}
	if (modificator.contains('ie')) {
	    println("detected modificator 'ie'")
	    buildParams["commandLine"].add("--BROWSER ie")
		buildParams["commandLine"].add("-m \"not not_ie\"")
		buildParams['valueTimeout'] = 180
	}
	if (modificator.contains('ff')) {
	    println("detected modificator 'ff'")
		buildParams["commandLine"].add("--BROWSER ff --BROWSER_FF_DISABLE_BEFOREUNLOAD True")
		buildParams['valueTimeout'] = 60
	}
	if (modificator.contains('iphonex')) {
	    println("detected modificator 'iphonex'")
		buildParams["commandLine"].add("--CHROME_MOBILE_EMULATION \"iPhone X\"")
		buildParams["commandLine"].add("--TAGS_TO_START mobile")
	}
	if (modificator.contains('iphonese')) {
	    println("detected modificator 'iphonese'")
		buildParams["commandLine"].add("--CHROME_MOBILE_EMULATION \"iPhone SE\"")
		buildParams["commandLine"].add("--TAGS_TO_START mobile")
    }
	if (modificator.contains('pixel5')) {
	    println("detected modificator 'pixel5'")
		buildParams["commandLine"].add("--CHROME_MOBILE_EMULATION \"Pixel 5\"")
		buildParams["commandLine"].add("--TAGS_TO_START mobile")
	}
	if (modificator.contains('galaxytabs4')) {
	    println("detected modificator 'galaxytabs4'")
		buildParams["commandLine"].add("--CHROME_MOBILE_EMULATION \"Galaxy Tab S4\"")
		buildParams["commandLine"].add("--TAGS_TO_START mobile")
	}
	if (modificator.contains('ipadmini')) {
	    println("detected modificator 'ipadmini'")
		buildParams["commandLine"].add("--CHROME_MOBILE_EMULATION \"iPad Mini\"")
		buildParams["commandLine"].add("--TAGS_TO_START mobile")
	}
	if (modificator.contains('plugin')) {
	    println("detected modificator 'plugin'")
		buildParams['serverAddress'] = "--SERVER_ADDRESS http://autotest-xp1:4444/wd/hub"
		buildParams['commandLine'].add("--TAGS_TO_START sbis_plugin")
		buildParams['commandLine'].add("--NOTIFICATOR DEFAULT")
	}
	if (modificator.contains('sbis3plugin')) {
	    println("detected modificator 'sbis3plugin'")
		buildParams['serverAddress'] = "--SERVER_ADDRESS http://test-selenium9:4443/wd/hub"
		buildParams['commandLine'].add("--TAGS_TO_START sbis_plugin")
		buildParams['commandLine'].add("--NOTIFICATOR DEFAULT")
	}
	if (modificator.contains('only') && (standName == 'test')) {
	    println("detected modificator 'only' and stand_name 'test'")
		buildParams['configPath'] = "config/${standWithUnit.replace('-', '_')}_only.ini"
	} else if (modificator.contains('only')) {
        // на fix 2 схемы поэтому 2 пользователя, на бою одна, а настройки называются по разному
		if (standName.size() > 0) {
		    println("detected modificator 'only'")
			buildParams["userOptions"].add('ADMIN_VIEW_INSIDE=автотестУЦ')
		} else {
    	    println("detected modificator 'only' and stand_name 'prod'")
			buildParams["userOptions"].add('ADMIN_VIEW_INSIDE=автотест2')
		}
		buildParams["userOptions"].add('ADMIN_VIEW_NAME=автотест2')
		buildParams["userOptions"].add('"ADMIN_VIEW_PASSWORD=ckj;ysqgfhjkm123"')
		buildParams["userOptions"].add('INSIDE_ONLY=True')
	}
	if (modificator.contains('api')) {
	    println("detected modificator 'api'")
		buildParams['serverAddress'] = ""
		buildParams['commandLine'].add("--API_SET_ACTIVITY True")
	}
	if (modificator.contains('debug')) {
		println("detected modificator 'debug'")
		buildParams['userOptions'].add("DEBUG_MODE=true")
		buildParams['userOptions'].add("js_collect=${jcAddress}:7676")		
		buildParams['commandLine'].add("--HIGHLIGHT_ACTION True")
	}
	if (modificator.contains('eng')) {
		println("detected modificator 'eng'")
		buildParams['userOptions'].add("LANG=eng")
		buildParams['commandLine'].add("--TAGS_TO_START localization")		
	}
	if (modificator.contains('crash')) {
		println("detected modificator 'crash'")
		buildParams['commandLine'].add("--TAGS_TO_START crash_test")
	}
	if (modificator.contains('myrussia')) {
        buildParams["commandLine"].add("-m \"not not_myrussia\"")
		if (standName == 'fix') {
			buildParams['commandLine'].add("--SITE https://fix.финансы.мояроссия.рф")
		} else {  // пока есть только на бою и на фиксе
			buildParams['commandLine'].add("--SITE https://финансы.мояроссия.рф")
		}
    }
	if (modificator.contains('old')){
		buildParams['commandLine'].add("--CHROME_BINARY_LOCATION 'C:\\inetpub\\selenoid\\chrome_old\\chromium\\chrome.exe' --BROWSER_VERSION  OLD")
	}
	
	return buildParams
}

// Получаем добавочный преффикс папки тестов исходя из массива модификаторов
def getJobFolderPathPreffix(modificator){
	result = ""
	for (val in modificator){
		result+= "-$val"
	}
	return result
}

// Проверяем корректность коммита, на который указывает HEAD
def checkHead(ver) {
	def get_head = [returnStdout: true, script: """git cat-file -t HEAD || exit 0"""]
	def set_ref = [returnStdout: true, script: """git symbolic-ref HEAD refs/heads/${ver}|| exit 0"""]
	def res
	if (isUnix()){
		res = sh(get_head)
		if (res.trim() != "commit") {
			sh(set_ref)
		}
	} else{
		res = bat(get_head)
		if (res.trim() != "commit") {
			bat(set_ref)
		}
	}
}

// Получаем путь до ветки из передаваемого URL параметра
def getBranchPath(url) {
	return (url.contains("__") ? url.replace("__", "/") : url)
}


def checkoutJobList(repoProduct, version, jobName, jobProduct) {
	dir("jobs") {
		if (jobProduct == 'my') {
			jobsTemplateFolder = 'jobs-my'
		}
		else if (jobName.contains('-demo')) {
			jobsTemplateFolder = 'jobs-demo'
		}
		else {
			jobsTemplateFolder = 'jobs'
		}

		repo = "git@git-autotests.sbis.ru:autotests/${repoProduct}.git"
		checkoutGitFolder([jobsTemplateFolder], version, repo)

		if (nameWithoutFolder.contains("smoke")) {
			filePath = "jobs/${jobsTemplateFolder}/smoke.json"
		} else {
		    if (repoProduct == 'inside' && jobsTemplateFolder == 'jobs') {
		        filePath = "jobs/${jobsTemplateFolder}/acceptance/"
		    }
		    else {
		        filePath = "jobs/${jobsTemplateFolder}/acceptance.json"
		    }
		}
		return filePath
	}
}

//Из params получаем словарь, с key- Название репозитория, Value-ветка, из которой выкачиваем этот репозиторий
def getRepPath(params) {
    repPaths = [:]
    params.each {param ->
        if (param.key.endsWith("_BRANCH") && !param.key.startsWith("ATF") && param.value) {
            repPaths[param.key.replace("_BRANCH", "")] = param.value
            }
    }
    return repPaths
}

//Удаляем временные папки с репозиториями, если они есть
def deleteRep(repPaths) {
    path_env = '/home/jenkins/environment_git/'
    repPaths.each {repPath ->
        if (repPath.key == "HELPERS" && repPath.value) {
            rep_value = repPath.value
            folder_rep = rep_value.split('/')[0]
            dir_path = path_env + "helpersFeature/${folder_rep}"
            dir(dir_path) {
                deleteDir()
            }
        } else if (repPath.value) {
            rep_value = repPath.value
            rep_name = repPath.key.toLowerCase()
            folder_rep = "pages/${rep_name}/" + rep_value.split('/')[0]
            dir_path = path_env + folder_rep
            dir(dir_path) {
                deleteDir()
            }
        }
    }
}

def notifyByLogins(logins, telegram, online) {
	def jcAddress = 'http://jenkins-control.tensor.ru'
	if (currentBuild.result != 'SUCCESS') {
		def json = JsonOutput.toJson([
			job_url: env.JOB_URL, build: env.BUILD_ID, job_name: env.JOB_NAME,
			logins: logins, telegram: telegram, online: online
		])
		httpRequest acceptType: 'APPLICATION_JSON_UTF8', consoleLogResponseBody: true, contentType: 'APPLICATION_JSON_UTF8',
					httpMode: 'POST', requestBody: json, responseHandle: 'NONE', timeout: 0, validResponseCodes: '100:502',
					url: "${jcAddress}/api/alert/send_by_login"
	}
}

// получение дефотного значения параметра, чтобы pipeline его не перетирал, если мы задали что то через интерфейс
@NonCPS
def getDefaultValueParamByName(name, defaultValue='') {
    def jobParams = currentBuild.rawBuild.project.getProperty('hudson.model.ParametersDefinitionProperty')
    if (jobParams) {
        def paramValue = jobParams.getParameterDefinition(name)
        if (paramValue != null) {
            return paramValue.getDefaultParameterValue().getValue()    
        }
    }
  	return defaultValue
}

// чтобы при выставлении параметров из pipeline не терять триггеры сборки
@NonCPS
def getTriggers() {
	def jobTriggers = currentBuild.rawBuild.project.getProperty('org.jenkinsci.plugins.workflow.job.properties.PipelineTriggersJobProperty')
	if (jobTriggers) {
		return jobTriggers.getTriggers()
	}
  	return []
}

def getJobParams(product) {
	// считываем дефолтные значения, тк pipeline их оферайдит, если мы выставили их через UI
	def paramsList = [
		choice(choices: ['ALL', 'FAILED', 'FAILED_WITHOUT_ERRORS'], description: '', name: 'BUILD_MODE'),
        booleanParam(defaultValue: false, description: 'Обновить библиотеки (page object, atf)', name: 'UPDATE'),
		booleanParam(defaultValue: true, description: 'Перезапускать упавшие тесты в конце сборки?', name: 'RESTART_TESTS')
	];
	def defaultValueBranch = getDefaultValueParamByName('BRANCH')
    paramsList.add(string(defaultValue: defaultValueBranch, description: 'ветка из которой запускаются тесты', name: 'BRANCH', trim: true))
    def dependancy = getDependancy(product, null)
    for (item in dependancy) {
		if (item != "ATF" && item != getProductPages(product).toUpperCase()) {
			paramsList.add(string(defaultValue: '', description: "ветка ${item}", name: "${item}_BRANCH", trim: true))
		}
	}
    paramsList.add(string(defaultValue: '', description: 'ветка HELPERS', name: 'HELPERS_BRANCH', trim: true))
	def defaultValueUserOptions = getDefaultValueParamByName('USER_OPTIONS')
    paramsList.add(text(defaultValue: defaultValueUserOptions, description: 'Пользовательские параметры', name: 'USER_OPTIONS', trim: true))
	def defaultValueControlsBranch = getDefaultValueParamByName('CONTROLS')
    paramsList.add(string(defaultValue: defaultValueControlsBranch, description: 'ветка CONTROLS', name: 'CONTROLS', trim: true))
	def defaultValueAtfBranch = getDefaultValueParamByName('ATF_BRANCH')
    paramsList.add(string(defaultValue: defaultValueAtfBranch, description: 'ветка ATF', name: 'ATF_BRANCH', trim: true))
	paramsList.add(booleanParam(defaultValue: true, description: 'Headless Mode Browser', name: 'HEADLESS'))
	paramsList.add(choice(choices: '40\n20\n10\n5\n1', name: 'STREAMS_NUMBER', description: 'Количество браузеров'))
	def defaultValueCheckUnit = getDefaultValueParamByName('CHECK_UNIT', false)
    paramsList.add(booleanParam(defaultValue: defaultValueCheckUnit, description: 'Проверить юнит', name: 'CHECK_UNIT'))
    def defaultValueDocker = getDefaultValueParamByName('DOCKER', false)
    paramsList.add(booleanParam(defaultValue: defaultValueDocker, description: 'запускать docker', name: 'DOCKER'))
    def defaultValueOperatorDependency = getDefaultValueParamByName('OPERATOR_DEPENDENCY', false)
    paramsList.add(booleanParam(defaultValue: defaultValueOperatorDependency, description: 'Обновить библиотеки для тестов ОФД (CTT, OTF, OFD, OFDTOOLS)', name: 'OPERATOR_DEPENDENCY'))
	paramsList.add(booleanParam(defaultValue: false, description: 'Сохранить эталонный видео файл', name: 'SAVE_STANDARD_VIDEO'))
    return paramsList
}


def commandLineStrToMap(strValue){
    """Преобразует строку с параметрами командной строки в словарь"""
        def startDict = [:]
        currentList = strValue.split("--")[1..-1]
        for (item in currentList){
            item = item.trim()
            a = item.split(" ", 2)
            startDict.put(a[0], (a.size() > 1 ? a[1] : ""))
        }
        return startDict
}

def overrideCommandLine(defaultStr, userStr){
    """Оверайд параметров командной строки"""

    println("defaultStr: ${defaultStr}")
    println("userStr: ${userStr}")

    defaultMap = commandLineStrToMap(defaultStr)
    println("defaultMap: ${defaultMap}")
    userMap = commandLineStrToMap(userStr)
    println("userMap: ${userMap}")

    resultMap = defaultMap + userMap
    println("resultMap: ${resultMap}")
    resultCommandLine = []
    for (def key in resultMap.keySet()) {
        keyValue = resultMap[key]
        if (keyValue == 'None')
            {
                println("Параметр не берем в командную строку: ${key}")
            }
        else {
            resultCommandLine.add("--${key} ${keyValue}".trim())
        }
    }
    resultCommandLine = resultCommandLine.join(' ')
    return resultCommandLine
    }

def wait_updates(stand){
    """Метод получает обновления на стенде и ждет их завершения"""

    if (stand in ['fix', 'test', 'pre-test']){
        println("check run updatest. wait_updates stand: ${stand}")

        def url = "http://mops.ci-info.sbis.ru:5000/api/stat/update_critical?stand=${stand}"
        def timeStart = LocalDateTime.now()
        while(true){
                sleep(2)
                def timeStop = LocalDateTime.now()
                Duration duration = Duration.between(timeStart, timeStop)
                def second = duration.toSeconds()
                try {
                    def response = httpRequest acceptType: 'APPLICATION_JSON_UTF8', consoleLogResponseBody: false,
                        contentType: 'APPLICATION_JSON_UTF8', quiet: true,
                        httpMode: 'GET', timeout: 0, url: "${url}"

                    def json= new JsonSlurper().parseText(response.content)
                    if (json.result == false){
                        println("Обновлений на стенде нет")
                        break
                    }
                    else {
                        println("Ожидаем завершения обновления на стенде ${second} сек")
                    }
                }
                catch(Exception err){
                     println("Не смогли получить статус обновлений на стенде\nОшибка: ${err}")
                     break
                }
                if (duration.toMinutes()>30){
                    println('Не смогли дождаться завершения обновления на стенде')
                    break
                }
        }
    }
}

def getHashMD5(String str) {
	MessageDigest digest = MessageDigest.getInstance("MD5");
	byte[] hash = digest.digest(str.getBytes(StandardCharsets.UTF_8));
	String hashStr = DatatypeConverter.printHexBinary(hash).toLowerCase();
	return hashStr
}


def jcRunningTestsOnFix() {
	/*Получить статус тестов из JC*/

	def run= true
	def jcAddress = 'http://jenkins-control.tensor.ru'
	def body = JsonOutput.toJson(['stand' : 'fix'])
	def response = httpRequest acceptType: 'APPLICATION_JSON_UTF8', consoleLogResponseBody: false,
			contentType: 'APPLICATION_JSON_UTF8', quiet: true, requestBody: body, validResponseCodes: '100:502',
			httpMode: 'POST', timeout: 0, url: "${jcAddress}/api/state/get"
	def json= new JsonSlurper().parseText(response.content)

	run = json.get('result', []).get('block', true)
	def value = json.get('result', []).get('value', "")
	if (value){
		/*если фикс занят смоками, не ждать*/
		if (value.contains("smoke")){
			run = false
		}
	}

	return run


}

def waitTestsOnFix() {
	/* Ожидаем пока завершатся тесты на фиксе */

	def timeStart = LocalDateTime.now()
	while (true) {
		def timeStop = LocalDateTime.now()
		Duration duration = Duration.between(timeStart, timeStop)
		def second = duration.toSeconds()
		try {
			def run_fix = jcRunningTestsOnFix()
			if (run_fix == false) {
				println("Сборки FIX не запущены")
				break
			} else {
				println("Ждем завершения сборок на FIX  ${second} сек")
			}
		}
		catch (Exception err) {
			println("Не смогли получить статус обновлений на стенде\nОшибка: ${err}")
			break
		}
		if (duration.toMinutes() > (60 * 3)) {
			println('Не смогли дождаться завершения сборок FIX')
			break
		}
		sleep(30)
	}
}