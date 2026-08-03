@Library('mylib@main') _

// Подменяем адрес продакшен-сервера на игровой сервер для деплоя
env.PROD_SERVER_IP = env.GAME_SERVER_IP

// Настраиваем Docker-демон на целевом сервере для доступа к локальному реестру
node('built-in') {
    stage('Fix Target Docker DNS/Registry') {
        sshagent(credentials: [env.SERVER_USER]) {
            sh """
            ssh -o StrictHostKeyChecking=no ${env.SERVER_USER}@${env.PROD_SERVER_IP} '
            if ! grep -q "${env.REGISTRY_IP}:5050" /etc/docker/daemon.json 2>/dev/null; then
                echo "Fixing Docker insecure-registries..."
                echo "{\\"insecure-registries\\\":[\\"${env.REGISTRY_IP}:5050\\"]}" | sudo tee /etc/docker/daemon.json
                sudo systemctl restart docker
                sleep 5
            fi
            '
            """
        }
    }
}

declarativePipeline(agent: 'built-in')
