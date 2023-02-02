pipeline{
    agent{
        label "acceptance"
    }
    options {
    disableConcurrentBuilds()
    buildDiscarder(
            logRotator(artifactDaysToKeepStr: '',
                    artifactNumToKeepStr: '',
                    daysToKeepStr: '5',
                    numToKeepStr: '3')
    )
    }
    parameters {
        booleanParam(defaultValue: false, description: 'Перезапустить только упавшие тесты', name: 'BUILD_ONLY_FAIL')
        booleanParam(defaultValue: false, description: 'Обновить библиотеки (page object, atf)', name: 'UPDATE')
        choice(choices: '40\n5\n1', name: 'STREAMS_NUMBER', description: 'Количество браузеров')
        booleanParam(defaultValue: true, description: 'Перезапускать упавшие тесты в конце сборки?', name: 'RESTART_AFTER_BUILD_MODE')
        booleanParam(defaultValue: true, description: 'Headless Mode Browser', name: 'HEADLESS')
    }
    fileLoader.withGit('https://github.com/ArturGayazov/html_academy_autotest.git', 'master', env.CREDENTIAL_ID_GIT, env.NODE_NAME) {
                    helpers = fileLoader.load('helpers.groovy')
    def repo_url = "git@git-autotests.sbis.ru:autotests/inside.git"
    def repoPath = "test-salary/smoke/test-salary"

    pathTests = helpers.checkoutTests(repoUrl, repoPath)
}