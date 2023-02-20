{ -> }

def building(repo, repoPath, setStatusCheck=true, comLineOpt=null, keep_folder=false) {
    def helpers = load 'helpers.groovy'    

    // try {
    def valueTimeout = 180
    def authService = 'http://auth-autotests.tensor.ru:5000'
    def commandLine = []
	if (comLineOpt) {
		commandLine.add(comLineOpt)
	}

    def userOptions = []
    def saby = false
    def disk = helpers.getDiskName()  // имя мапнутого диска на ноде с браузером

    // получаем имя сборки без папки
    if (JOB_NAME.contains('/')) {
        jobNameStrict = JOB_NAME.split('/')[1]
    } else {
        jobNameStrict = JOB_NAME
    }

    // стенд + юнит
    standWithUnit = jobNameStrict.split(' ')[1]
    pathTests = standWithUnit

    def standWithUnitMap = helpers.parseNameJob(standWithUnit)
    def standName = standWithUnitMap.get("stand", "")
    def product = standWithUnitMap.get('product').toUpperCase()
    def unit = standWithUnitMap.get('unit', null)

    def paramsJob = helpers.getJobParams(product);
    // генерим имя папки, если это smoke тесты, то добавляем -smoke
    if (jobNameStrict.contains('smoke')) {
        valueTimeout = 30
    }
	if (!jobNameStrict.contains('smoke')) {
        paramsJob.add(booleanParam(defaultValue: false, description: 'Запустить сборки совместимости', name: 'COMPATIBILITY'))
    }
    
    // обновляем виртуальное окружение
    def branch = null;
    if (params.get('BRANCH')) {
        branch = params.get('BRANCH')
    }

    def atf_branch = null;
    if (params.get('ATF_BRANCH')) {
        atf_branch = helpers.getBranchPath(params.get('ATF_BRANCH'))
    }
    def pythonBin = "/home/jenkins/envs/selenium4/bin/python3.7";
    println(">>>>>>> pythonBin: $pythonBin")

    def controlsVer = null;
    if (params.get('CONTROLS')) {
        controlsVer = helpers.getBranchPath(params.get('CONTROLS'))
    }

    def updateEnv = env.UPDATE == 'true'
    def tmp = helpers.updateEnvorimentGit(product, updateEnv, false, branch, false, unit, atf_branch, controlsVer, params.get('OPERATOR_DEPENDENCY'))
    def ver = tmp[0]
    def pythonPath = tmp[1]
    def serverAddress = "--SERVER_ADDRESS " + tmp[2].replace('--DISPATCHER_RUN_MODE', '--TG_RUN_MODE')
    def repPaths = tmp[3]

    // модификатор тестов (браузер, inside-only, версия ос)
    modificator = helpers.getListModificators()
    if (modificator) {
        pathTests += helpers.getJobFolderPathPreffix(modificator)
        buildParams = helpers.getBuildParams(modificator, standName)
        commandLine += buildParams.get("commandLine")
        userOptions += buildParams.get("userOptions")
        serverAddress = buildParams.get("serverAddress", serverAddress)
        valueTimeout = buildParams.get("valueTimeout", valueTimeout)
        if (buildParams.get("configPath")){
            configPath = buildParams.get("configPath")
        }
    }

    if (product in ['ONLINE', 'INSIDE', 'MY']) {        
        paramsJob.add(choice(choices: 'online\nsaby\nclient\nclient.sabyc\nmyrussia', name: 'DOMAIN', description: 'run tests on domain'))
    }

    properties([
        disableConcurrentBuilds(),
        buildDiscarder(
            logRotator(artifactDaysToKeepStr: '',
                       artifactNumToKeepStr: '',
                       daysToKeepStr: '7',
                       numToKeepStr: '14')),
        parameters(paramsJob),
        pipelineTriggers(helpers.getTriggers())
    ])
	
	def currentName = []
    paramDomain = params.get('DOMAIN')
    if (paramDomain && paramDomain != 'online') {
        def tmpProduct = product
        if (tmpProduct == 'INSIDE') {
            tmpProduct = "ONLINE"
        }
        
        if (paramDomain == 'client') {
            if (tmpProduct == "ONLINE") {
                commandLine.add("--SITE https://fix-client.sbis.ru")
            }
        }
        if (paramDomain == 'client.sabyc') {
            if (tmpProduct == "ONLINE") {
                commandLine.add("--SITE https://fix-client.sabyc.ru")
            }
        }
        if (paramDomain == 'saby') {
            if (tmpProduct == 'SBIS') {
                commandLine.add("--SITE https://fix.saby.ru")
            } else {
                if (standName) {
                    commandLine.add("--SITE https://${standName}-${tmpProduct.toLowerCase()}.saby.ru")            
                } else {
                    commandLine.add("--SITE http://${tmpProduct.toLowerCase()}.saby.ru")
                }
            }  
        }
		currentName.add(paramDomain)
    }

    if (params.get('HEADLESS')) {
        commandLine.add('--HEADLESS_MODE new')
    }
	
	if (params.get('DOCKER')) {
        commandLine.add('--DOCKER True')
    }
	
	if (params.get('CHECK_UNIT')){
		    
			def check_unit
			if (unit) {
				if (unit == 'autotests'){
					check_unit = "autotests-inside"
				}
				else if (unit == "ext-2"){
					check_unit = "ext2"
				}
				else{
			    check_unit = unit
				}
			}
			else {
			    check_unit = product.toLowerCase()
			}
			commandLine.add("--UNIT_FOR_CHECK \"${check_unit}\"")
	}
	
	if (params.get('COMPATIBILITY')) {
        commandLine.add('--TAGS_TO_START production_online')
		currentName.add('COMP')
    }

    if (params.get('USER_OPTIONS')) {
        for (i in params.get('USER_OPTIONS').split(' ')) {
			userOptions.add(i)
		}
    }

    // для приемочных на production запускаем только протегированные
    if ((!standName && !jobNameStrict.contains('smoke')) || (modificator.contains('only')))  {
        def depTags = [
            'INSIDE': 'production_inside',
            'ONLINE': 'production_online',
            'MY': 'production_online',
            'CLOUD': 'production'
        ]
        def tag = depTags.get(product, "")
        if (tag.size() > 0) {
            commandLine.add("--TAGS_TO_START \"${tag}\"")
        }
    }
    // для приемочных на vip64 запускаем только протегированные
    if ((unit == 'ext-vip64') && !jobNameStrict.contains('smoke'))  {
        commandLine.add("--TAGS_TO_START \"ext64\"")
    }

    def milestone = helpers.getMilestoneSettings(standName, product, unit)
    println("MILESTONE: ${milestone}")
    commandLine.add("--MILESTONE \"${milestone}\"")
    // автовыстовление статуса в проверках, кроме боевых сборок
    if ( setStatusCheck == true && standName ) {
        commandLine.add("--SET_STATUS_CHECK")
    }

    // развилка для запуска тестов совместимости
    def productGit = helpers.getProductPages(product)
    folderName = repoPath.replace('/', '_')    
    pathTests += "/${folderName}"

    // определяем папку с тестами, которая будет удаляться по завершению тестов
    homePath = "/home/jenkins/tests/"
    testFolderPath = homePath + pathTests
    removeFolderPath = testFolderPath

    // эти продукты еще лежат в SVN
    dir(testFolderPath) {
        stage('Git clone') {
            def pages = 'pages_' + productGit
            if (branch) {  // если у нас feature ветка
                ver = branch
                pythonPath = pythonPath + ":${testFolderPath}"
                gitPath = [repoPath, pages]
                currentName.add(ver)
            } else {
                gitPath = [repoPath]
                def exists = fileExists pages
                if (exists) {
                    sh "rm -fr ./${pages}"
                }
            }
            // выкачиваем тесты из GIT
            println(">>>>>>> GIT CLONE BRANCH: $ver")
            println repoPath
            println ver
            println repo
            timeout(time: 10, unit: 'MINUTES') {
                lock("checkoutTests_${env.NODE_NAME}") {
                    if (product == 'SBIS') {
                        helpers.checkoutGitFolder(gitPath, ver, repo, null, null, true)
                    } else {
                        helpers.checkoutGitFolder(gitPath, ver, repo)
                    }
                }
            }
        }
        // когда делаем checkout он делается в папку относительно корня репозитория
        pathTests += "/${repoPath}"
    }

    configPath = "config/${standWithUnit.replace('-', '_')}.ini"
    println(">>>>>>> config: $configPath")

    dir(homePath + pathTests) {
        dir('test-reports') {
            deleteDir()
        }
        def downloadDir = pwd()
        def downloadDirBrowser="${disk}:\\${pathTests.replace('/', '\\')}"

        println(">>>>>>> downloadDir: $downloadDir")
        println(">>>>>>> downloadDirBrowser: $downloadDirBrowser")

        // HTTP PATH, ARTIFACT_PATH для скриншотов и других артефактов
        def hostname = sh returnStdout: true, script: 'echo $HOSTNAME'

        if (keep_folder) {
            artifactPath = pwd();
            commandLine.add("--HTTP_PATH \"http://${hostname.trim()}/${pathTests}\"")
        } else {
            String hashStr = helpers.getHashMD5(pathTests)
            artifactsCommonFolder = "artifacts_common"
            commandLine.add("--HTTP_PATH \"http://${hostname.trim()}/$artifactsCommonFolder/${hashStr}\"")
            artifactPath = "${homePath}${artifactsCommonFolder}/${hashStr}"
            println(">>>>>>> artifactFolder: ${artifactPath}")
        }

        // квота для tensor-grid
        def quota = standWithUnit.replace('-', '_')

		if (controlsVer) {
            currentName.add(controlsVer)
        }

        //Запустить только упавшие?
        if (params.get('BUILD_MODE') == 'FAILED') {
            commandLine.add("--START_FAIL")
			currentName.add('FAILED')
        }
		
		if (params.get('DOCKER')) {
			currentName.add('DOCKER')
		}

        if (atf_branch) {
            currentName.add(atf_branch)
        }
		
        //Запустить только упавшие НЕ по ошибкам?
        if (params.get('BUILD_MODE') == 'FAILED_WITHOUT_ERRORS') {
            commandLine.add("--RESTART_FAIL_WITHOUT_ERRORS")
			currentName.add('FAILED_WITHOUT_ERRORS')
        }
		
		//Записываем параметры в название билда сборки, если они были переданы
		if (currentName) {
			currentName.add(0, currentBuild.displayName)
			currentBuild.displayName = currentName.join(' / ')
        }
        // количество потоков
        streams_number = params.get('STREAMS_NUMBER')
        if (!streams_number) {
            streams_number = 40
        }
        commandLine.add("--STREAMS_NUMBER ${streams_number}")
        
        // лимит свободной памяти
        commandLine.add("--RUNNER_LIMIT_FREE_MEMORY 3000")

        // сделано так, чтобы перезапуск был при 1 запуске, когда настройки нет, а не просто через true
        if (params.get('RESTART_TESTS')) {
            commandLine.add('--RESTART_AFTER_BUILD_MODE')
        }

        if (params.get('SAVE_STANDARD_VIDEO')) {
            commandLine.add("--HIGHLIGHT_ACTION True")
            commandLine.add("--SCREEN_CAPTURE all")
            commandLine.add("--EXTERNAL_ARTIFACT_STORAGE http://test-autotest-storage.unix.tensor.ru")
            commandLine.add("--EXTERNAL_ARTIFACT_PATH ${pathTests}/")
        } else {
            commandLine.add("--SCREEN_CAPTURE \"video\"")
        }

        dir(artifactPath) {
            // download result.db
            helpers.getResultDB()
        }
				
		def versionJSON = helpers.saveVersions(quota)
		if (versionJSON && versionJSON.build && versionJSON.version) {
            println("VERSION: ${versionJSON.build}")
            commandLine.add("--PRODUCT_BUILD \"${versionJSON.build}\"")
            println("VERSION: ${versionJSON.version}")
            commandLine.add("--PRODUCT_VERSION \"${versionJSON.version}\"")
        }
		
        commandLine = commandLine.join(' ')
        if (userOptions.size() > 0) {
            userOptions = '--USER_OPTIONS ' + userOptions.join(' ')
        } else {
            userOptions = ""
        }

        def resultCommandLine = """${commandLine} --BROWSER_PAGE_LOAD_STRATEGY eager --DISABLE_GPU True --GIT_REPOSITORY ${repo} --GIT_PATH ${repoPath} --SKIP_TESTS_FROM_JC --AUTH_SERVICE_ADDRESS ${authService} --PATH_INIT $configPath --WAIT_ELEMENT_LOAD 20 --DELAY_ACTION 0 --JOB "${env.JOB_NAME}" --JOB_URL "${env.JOB_URL}" --BUILD ${env.BUILD_NUMBER} $serverAddress --TG_QUOTA $quota --DOWNLOAD_DIR "${downloadDir}" --DOWNLOAD_DIR_BROWSER "${downloadDirBrowser}" """
        if (comLineOpt) {
            resultCommandLine = helpers.overrideCommandLine(resultCommandLine, comLineOpt)
        }

        timeout(time: valueTimeout, unit: 'MINUTES') {
            stage ('Running tests') {
                def execCommand
                def exists = fileExists 'start_tests.py'
                if (exists) {
                    execCommand = "start_tests.py"
                } else {
                    execCommand = "-c \"from atf.run import RunTests;RunTests().run_tests()\""
                }
                sh """
                export ATF_ARTIFACT_PATH=${artifactPath}
                ${pythonPath}
                ${pythonBin} ${execCommand} ${resultCommandLine} ${userOptions}
                """
            }
        }
        junit keepLongStdio: true, skipOldReports: true, testResults: 'test-reports/*.xml'
        dir(artifactPath) {
            archiveArtifacts "result.db"
        }
    }
    if (!keep_folder && removeFolderPath) {
        println("Удаляем директорию с тестами: $removeFolderPath")
        dir(removeFolderPath) {
            deleteDir()
        }
    }
    // } catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
    //     currentBuild.result = 'ABORTED'
    // } catch (Exception e) {
    //     println e
    //     currentBuild.result = 'FAILURE'
    // } finally {
    //     stage('save build status in jc') {
    //         helpers.save_build_result_to_jc(currentBuild.currentResult)
    //     }
    // }
}
