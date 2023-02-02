{ -> }

def building(repo, repoPath, setStatusCheck=true, comLineOpt=null, keep_folder=false) {
    def helpers = load 'helpers.groovy'
    // def authService = 'http://auth-autotests.tensor.ru:5000'
    def valueTimeout = 120
    def commandLine = []
    def userOptions = []
    // def saby = false

    // try {

    // получаем имя сборки без папки
    if (JOB_NAME.contains('/')) {
        jobNameStrict = JOB_NAME.split('/')[1]
    } else {
        jobNameStrict = JOB_NAME
    }

    // стенд + юнит
    standWithUnit = jobNameStrict.split(' ')[1]

    pathTests = standWithUnit
    // генерим имя папки, если это smoke тесты, то добавляем -smoke
    // if (jobNameStrict.contains('smoke')) {
    //     pathTests += '-smoke'
    //     valueTimeout = 30
    // }

    def configPath = "config.ini"
    // def standWithUnitMap = helpers.parseNameJob(standWithUnit)
    // def standName = standWithUnitMap.get("stand", "")
    def product = ''
    // def unit = standWithUnitMap.get('unit', null)
    def paramsJob = helpers.getJobParams(product);
    // println("unit: ${unit}")
    // def milestone = helpers.getMilestoneSettings(standName, product, unit)
    // println("MILESTONE: ${milestone}")
    // commandLine.add("--MILESTONE \"${milestone}\"")
    // обновляем виртуальное окружение
    def branch = null;
    if (params.get('BRANCH')) {
        branch = params.get('BRANCH')
    }
    // def atf_branch = null;
    // if (params.get('ATF_BRANCH')) {
    //     atf_branch = helpers.getBranchPath(params.get('ATF_BRANCH'))
    // }
    def pythonBin = "/Users/artur_gaazov/Documents/html_academy/venv"; //!!!ВАЖНАЯ СТРОКА

    // def controlsVer = null;
    // if (params.get('CONTROLS')) {
    //     controlsVer = helpers.getBranchPath(params.get('CONTROLS'))
    // }
    // def updateEnv = params.get('UPDATE')
    // def tmp = helpers.updateEnvorimentGit(product, updateEnv, false, branch, false, unit, atf_branch, controlsVer, params.get('OPERATOR_DEPENDENCY'))
    def pythonPath = pythonBin
    // def ver = tmp[0]
    // def serverAddress = "--SERVER_ADDRESS " + tmp[2]
    // def repPaths = tmp[3]

    if (params.get('HEADLESS')) {
        commandLine.add('--HEADLESS_MODE True')
    }
    
    if (params.get('DOCKER')) {
        commandLine.add('--DOCKER True')
    }
    
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

    def currentName = []
    if (atf_branch) {
        currentName.add(atf_branch)
    }
    if (controlsVer) {
        currentName.add(controlsVer)
    }
    if (product in ['ONLINE', 'INSIDE', 'MY']) {
        paramsJob.add(choice(choices: 'online\nsaby', name: 'DOMAIN', description: 'run tests on domain'))
    }

    // paramDomain = params.get('DOMAIN')
    // if (paramDomain && paramDomain == 'saby') {
    //     def tmpProduct = product
    //     if (tmpProduct == 'SBIS') {
    //         commandLine.add("--SITE https://${standName}.saby.ru")
    //     } else {
    //         if (tmpProduct == 'INSIDE') {
    //             tmpProduct = "ONLINE"
    //         }
    //         commandLine.add("--SITE https://${standName}-${tmpProduct.toLowerCase()}.saby.ru")
    //     }
    //     currentName.add(paramDomain)
    // }

    // параметры сборки
    properties([
        disableConcurrentBuilds(),
        buildDiscarder(
            logRotator(artifactDaysToKeepStr: '',
                       artifactNumToKeepStr: '',
                       daysToKeepStr: '7',
                       numToKeepStr: '7')),
        parameters(paramsJob),
        pipelineTriggers(helpers.getTriggers())
    ])

    // для -eng
    // if (standWithUnit.endsWith('-eng')) {
    //     userOptions.add("LANG=eng")
    // }

    if (params.get('USER_OPTIONS')) {
        for (i in params.get('USER_OPTIONS').split(' ')) {
            userOptions.add(i)
        }
    }
    
    // if (params.get('CHECK_UNIT')) {
    //     def check_unit
    //     if (unit) {
    //         if (unit == 'autotests'){
    //             check_unit = "autotests-inside"
    //         }
    //         else if (unit == 'ext-autotest'){
    //             check_unit = "autotest-ext"
    //         }
    //         else{
    //             check_unit = unit
    //         }
    //     }
    //     else {
    //         check_unit = product.toLowerCase()
    //     }
    //     commandLine.add("--UNIT_FOR_CHECK \"${check_unit}\"")
    // }

    // для приемочных на production запускаем только протегированные
    // if ((!standName && !jobNameStrict.contains('smoke')) || (modificator.contains('only') && standName != 'test'))  {
    //     def depTags = [
    //         'INSIDE': 'production_inside',
    //         'ONLINE': 'production_online',
    //         'MY': 'production_online',
    //         'CLOUD': 'production'
    //     ]
    //     def tag = depTags.get(product, "")
    //     if (tag.size() > 0) {
    //         commandLine.add("--TAGS_TO_START \"${tag}\"")
    //     }
    // }
    // для приемочных на vip64 запускаем только протегированные
    // if ((unit == 'ext-vip64') && !jobNameStrict.contains('smoke'))  {
    //     commandLine.add("--TAGS_TO_START \"ext64\"")
    // }

    // автовыстовление статуса в проверках, кроме боевых сборок
    // if ( setStatusCheck == true && standName ) {
    //     commandLine.add("--SET_STATUS_CHECK")
    // }

    // берем имя папки из пути в репозитории GIT
    folderName = repoPath.replace('/', '_')    
    pathTests += "/${folderName}"

    // определяем папку с тестами, которая будет удаляться по завершению тестов
    homePath = "/home/jenkins/tests/"
    testFolderPath = homePath + pathTests
    removeFolderPath = testFolderPath

    dir(testFolderPath) {
        stage('Git clone') {
            def pages = 'pages_' + helpers.getProductPages(product)
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
            println(">>>>>>> GIT CLONE BRANCH: ${repo};${repoPath};$ver")
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
    }

    // когда делаем checkout он делается в папку относительно корня репозитория
    pathTests += "/${repoPath}"

    dir(homePath + pathTests) {
        dir('test-reports') {
            deleteDir()
        }
        // папка сборки
        workspaceFolder = pwd()
        println(">>>>>>> workspaceFolder: $workspaceFolder")

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

        dir(artifactPath) {
            // download result.db
            helpers.getResultDB()
        }

        // DOWNLOAD_DIR_BROWSER
        // downloadDirBrowser="${disk}:\\${pathTests.replace('/', '\\')}"

        // квота для tensor-grid
        // def quota = standWithUnit.replace('-', '_')

        //Запустить только упавшие?
        if (params.get('BUILD_MODE') == 'FAILED') {
            commandLine.add("--START_FAIL")
            currentName.add('FAILED')
        }
        //Запустить только упавшие НЕ по ошибкам?
        if (params.get('BUILD_MODE') == 'FAILED_WITHOUT_ERRORS') {
            commandLine.add("--RESTART_FAIL_WITHOUT_ERRORS")
            currentName.add('FAILED_WITHOUT_ERRORS')
        }
        
        // if (params.get('DOCKER')) {
        //     currentName.add('DOCKER')
        // }
        
        //Записываем параметры в название билда сборки, если они были переданы
        if (currentName) {
            currentName.add(0, currentBuild.displayName)
            currentBuild.displayName = currentName.join(' / ')
        }
        
        // количество потоков
        streams_number = params.get('STREAMS_NUMBER', 40)        
        commandLine.add("--STREAMS_NUMBER ${streams_number}")

        if (params.get('SAVE_STANDARD_VIDEO')) {
            commandLine.add("--HIGHLIGHT_ACTION True")
            commandLine.add("--SCREEN_CAPTURE all")
            commandLine.add("--EXTERNAL_ARTIFACT_STORAGE http://test-autotest-storage.unix.tensor.ru")
            commandLine.add("--EXTERNAL_ARTIFACT_PATH ${pathTests}/")
        } else {
            commandLine.add("--SCREEN_CAPTURE \"video\"")
        }

        // лимит свободной памяти
        commandLine.add("--RUNNER_LIMIT_FREE_MEMORY 3000")

        // сделано так, чтобы перезапуск был при 1 запуске, когда настройки нет, а не просто через true
        if (params.get('RESTART_TESTS')) {
            commandLine.add('--RESTART_AFTER_BUILD_MODE')
        }
                
        // def versionJSON = helpers.saveVersions(quota)
        // if (versionJSON && versionJSON.build && versionJSON.version) {
        //     println(">>>>>>> PRODUCT VERSION: ${versionJSON.version} # ${versionJSON.build}")
        //     commandLine.add("--PRODUCT_VERSION \"${versionJSON.version}\"")
        //     commandLine.add("--PRODUCT_BUILD \"${versionJSON.build}\"")
        // }
        
        commandLine = commandLine.join(' ')
        if (userOptions.size() > 0) {
            userOptions = '--USER_OPTIONS ' + userOptions.join(' ')
        } else {
            userOptions = ""
        }

        def resultCommandLine = """${commandLine} --BROWSER_PAGE_LOAD_STRATEGY eager --DISABLE_GPU True --GIT_REPOSITORY ${repo} --GIT_PATH ${repoPath} --SKIP_TESTS_FROM_JC --AUTH_SERVICE_ADDRESS ${authService} --PATH_INIT $configPath --WAIT_ELEMENT_LOAD 20 --DELAY_ACTION 0 --JOB "${env.JOB_NAME}" --JOB_URL "${env.JOB_URL}" --BUILD ${env.BUILD_NUMBER} $serverAddress --TG_QUOTA $quota --DOWNLOAD_DIR "${workspaceFolder}" --DOWNLOAD_DIR_BROWSER "${downloadDirBrowser}" """
        if (comLineOpt) {
            resultCommandLine = helpers.overrideCommandLine(resultCommandLine, comLineOpt)
        }

        // Выполнить команду windows
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
