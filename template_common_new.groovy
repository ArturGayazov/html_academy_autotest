{ -> }

def building(repo, repoPath, setStatusCheck=true, comLineOpt=null, keep_folder=false) {
    def helpers = load 'helpers.groovy'  

    //Таймаут для принудительного завершения сборки
    def valueTimeout = 180

    //Список параметров для ввода в консоль
    def commandLine = []
    if (comLineOpt) {
        commandLine.add(comLineOpt)
	}

    // def disk = сюда желательно передать имя диска

    //Читаем параметры сборки
    //def paramsJob = helpers.getJobParams(product);
    
    //активируем виртуальное окружение проекта (желательно завязаться на глобмальное окружение)
    def python_venv_activate = 'source /Users/artur_gaazov/Documents/html_academy/venv/bin/activate'
    
    //прописываем путь до проекта (без этого не видит модули в нем)
    def project_path = 'export PYTHONPATH="/Users/artur_gaazov/Documents/html_academy"'

    //проваливаемся в папку с тестами
    def go_to_test = 'cd test_main_page'

    def start_tests = 'python3 start_tests.py --HEADLESS_MODE True --SCREEN_CAPTURE video'

    //клонируем проект
    timeout(time: 10, unit: 'MINUTES') {
            helpers.checkoutGitFolder(repoPath, 'master', repo, null, null, false)
    }

    timeout(time: valueTimeout, unit: 'MINUTES') {
    stage ('Running tests') {
        sh """
        ${python_venv_activate}
        ${project_path}
        ${go_to_test} 
        ${start_tests}
        """
    }
    }


}