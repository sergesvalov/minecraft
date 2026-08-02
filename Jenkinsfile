@Library('mylib@main') _

// Подменяем адрес продакшен-сервера на игровой сервер для деплоя
env.PROD_SERVER_IP = env.GAME_SERVER_IP

// Автоматически чиним настройки Docker на сервере, так как там сломан DNS
node('built-in') {
    stage('Fix Target Docker DNS/Registry') {
        sshagent(credentials: ['serge']) {
            sh """
            ssh -o StrictHostKeyChecking=no serge@${env.PROD_SERVER_IP} '
            if ! grep -q \"192.168.0.222:5050\" /etc/docker/daemon.json 2>/dev/null; then
                echo "Fixing Docker insecure-registries..."
                echo "{\\"insecure-registries\\\":[\\"192.168.0.222:5050\\"]}" | sudo tee /etc/docker/daemon.json
                sudo systemctl restart docker
                sleep 5
            fi
            '
            """
        }
    }
}

declarativePipeline(agent: 'built-in')
