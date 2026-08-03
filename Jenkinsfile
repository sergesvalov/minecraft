@Library('mylib@main') _

// Подменяем адрес продакшен-сервера на игровой сервер для деплоя
env.PROD_SERVER_IP = env.GAME_SERVER_IP

// Настраиваем Docker-демон на целевом сервере для доступа к локальному реестру
node('built-in') {
    stage('Prepare Server Scripts') {
        sshagent(credentials: [env.SERVER_USER]) {
            sh """
            ssh -o StrictHostKeyChecking=no ${env.SERVER_USER}@${env.PROD_SERVER_IP} '
            # Создаем папку для скриптов на сервере и даем права пользователю
            sudo mkdir -p /opt/minecraft/scripts
            sudo chown -R \$(whoami) /opt/minecraft/scripts
            '
            
            # Принудительно копируем скрипты из репозитория на сервер
            scp -o StrictHostKeyChecking=no -r scripts/* ${env.SERVER_USER}@${env.PROD_SERVER_IP}:/opt/minecraft/scripts/
            
            # Делаем скрипты исполняемыми
            ssh -o StrictHostKeyChecking=no ${env.SERVER_USER}@${env.PROD_SERVER_IP} 'chmod +x /opt/minecraft/scripts/*.sh'
            """
        }
    }
}

declarativePipeline(agent: 'built-in')
