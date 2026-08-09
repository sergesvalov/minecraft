@Library('mylib@main') _

// Override production server IP with game server IP for deployment
env.PROD_SERVER_IP = env.GAME_SERVER_IP

// Configure target node for deployment
node('built-in') {
    stage('Prepare Server Scripts') {
        sshagent(credentials: [env.SERVER_USER]) {
            sh """
            ssh -o StrictHostKeyChecking=no ${env.SERVER_USER}@${env.PROD_SERVER_IP} '
            # Create scripts directory on target server and set permissions
            sudo mkdir -p /opt/minecraft/scripts
            sudo chown -R \$(whoami) /opt/minecraft/scripts
            # Remove old scripts to prevent "Text file busy" error during scp if they are running
            rm -f /opt/minecraft/scripts/*.sh
            '
            
            # Force copy scripts from repository to server
            scp -o StrictHostKeyChecking=no -r scripts/* ${env.SERVER_USER}@${env.PROD_SERVER_IP}:/opt/minecraft/scripts/
            
            # Make scripts executable
            ssh -o StrictHostKeyChecking=no ${env.SERVER_USER}@${env.PROD_SERVER_IP} 'chmod +x /opt/minecraft/scripts/*.sh'
            """
        }
    }
}

declarativePipeline(agent: 'built-in')
