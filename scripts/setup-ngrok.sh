#!/usr/bin/env bash
# =============================================================================
# setup-ngrok.sh — Expõe Swagger e Console OpenShift na internet via ngrok
#
# CONTA GRATUITA (sem cartão):
#   1. Acesse: https://dashboard.ngrok.com/signup
#   2. Pegue seu authtoken em: https://dashboard.ngrok.com/get-started/your-authtoken
#   3. Pegue seu domínio estático em: https://dashboard.ngrok.com/cloud-edge/domains
#      (1 domínio grátis por conta, ex: algo-bonito.ngrok-free.app)
#
# USO:
#   bash setup-ngrok.sh <AUTHTOKEN> <STATIC-DOMAIN>
#   Exemplo:
#   bash setup-ngrok.sh 2abc123xyz_ABC meu-fgo.ngrok-free.app
#
# O QUE FAZ:
#   - Instala ngrok no servidor RHEL
#   - Configura o authtoken
#   - Cria /etc/ngrok/ngrok.yml com dois túneis:
#       meu-fgo.ngrok-free.app         → API (Swagger, logs, métricas)
#       (console via ngrok API port)   → Console OpenShift (só no plano pago)
#   - Instala como serviço systemd (sobe com o servidor)
#
# LIMITAÇÕES DO PLANO GRATUITO:
#   - 1 domínio estático
#   - 1 túnel simultâneo por agente
#   → Por isso expõe só a API (Swagger). O Console fica via oc CLI ou Tailscale.
# =============================================================================

set -euo pipefail

AUTHTOKEN="${1:-}"
STATIC_DOMAIN="${2:-}"

if [[ -z "$AUTHTOKEN" || -z "$STATIC_DOMAIN" ]]; then
    echo "Uso: bash $0 <AUTHTOKEN> <STATIC-DOMAIN>"
    echo ""
    echo "Onde obter:"
    echo "  AUTHTOKEN:     https://dashboard.ngrok.com/get-started/your-authtoken"
    echo "  STATIC-DOMAIN: https://dashboard.ngrok.com/cloud-edge/domains"
    echo "                 (crie um domínio grátis — ex: meu-fgo.ngrok-free.app)"
    exit 1
fi

# URL interna da rota OpenShift da aplicação
ROUTE_HOST="ms-operacao-fgo-backend.apps-crc.testing"

echo "=============================================="
echo " ngrok Tunnel — FGO"
echo " Domínio: https://$STATIC_DOMAIN"
echo " Alvo:    https://$ROUTE_HOST"
echo "=============================================="

# =============================================================================
# 1. Instalar ngrok
# =============================================================================
if ! command -v ngrok &>/dev/null; then
    echo "[1/5] Instalando ngrok..."
    # Repositório oficial ngrok para RHEL/CentOS
    curl -fsSL https://ngrok-agent.s3.amazonaws.com/ngrok.asc \
        | sudo tee /etc/yum.repos.d/ngrok.repo > /dev/null
    # Ou instala direto do binário (mais simples no RHEL):
    NGROK_TMP=$(mktemp -d)
    curl -fsSL "https://bin.equinox.io/c/bNyj1mQVY4c/ngrok-v3-stable-linux-amd64.tgz" \
        -o "$NGROK_TMP/ngrok.tgz"
    tar -xzf "$NGROK_TMP/ngrok.tgz" -C "$NGROK_TMP"
    sudo mv "$NGROK_TMP/ngrok" /usr/local/bin/ngrok
    rm -rf "$NGROK_TMP"
    echo "      ngrok instalado: $(ngrok version)"
else
    echo "[1/5] ngrok já instalado: $(ngrok version)"
fi

# =============================================================================
# 2. Autenticar
# =============================================================================
echo "[2/5] Configurando authtoken..."
ngrok config add-authtoken "$AUTHTOKEN"

# =============================================================================
# 3. Criar configuração YAML do ngrok
# =============================================================================
echo "[3/5] Criando configuração em /etc/ngrok/ngrok.yml..."
sudo mkdir -p /etc/ngrok

# Pega o caminho do config default criado pelo add-authtoken
DEFAULT_CFG="$HOME/.config/ngrok/ngrok.yml"
[[ ! -f "$DEFAULT_CFG" ]] && DEFAULT_CFG="$HOME/.ngrok2/ngrok.yml"

sudo tee /etc/ngrok/ngrok.yml > /dev/null <<EOF
# ngrok config — FGO
# Gerado em: $(date)
version: "2"
authtoken: $AUTHTOKEN

tunnels:
  # API principal (Swagger UI, operacoes, remessas, logs, métricas)
  # Acessa a rota OpenShift com host-header correto (SNI rewrite)
  fgo-api:
    proto: http
    addr: https://$ROUTE_HOST
    hostname: $STATIC_DOMAIN
    host_header: $ROUTE_HOST
    # Desabilita verificação TLS (OpenShift CRC usa cert self-signed)
    # O ngrok verifica o cert externamente; internamente aceita qualquer cert
    bind_tls: true
    inspect: false

EOF

echo "      Configuração criada."

# =============================================================================
# 4. Criar serviço systemd
# =============================================================================
echo "[4/5] Criando serviço systemd..."
NGROK_BIN=$(which ngrok)

sudo tee /etc/systemd/system/ngrok.service > /dev/null <<EOF
[Unit]
Description=ngrok Tunnel — FGO
Documentation=https://ngrok.com/docs
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=$(whoami)
ExecStart=$NGROK_BIN start --all --config /etc/ngrok/ngrok.yml
Restart=on-failure
RestartSec=10
# Espera a rede estar pronta
ExecStartPre=/bin/sleep 5
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable ngrok
sudo systemctl restart ngrok
sleep 5

STATUS=$(systemctl is-active ngrok 2>/dev/null || echo "erro")
echo "      Serviço ngrok: $STATUS"

# =============================================================================
# 5. Atualizar configmap do OpenShift com a URL pública
# =============================================================================
echo "[5/5] Atualizando APP_URL no ConfigMap do OpenShift..."
if command -v oc &>/dev/null && oc whoami &>/dev/null 2>&1; then
    oc patch configmap ms-operacao-config -n fgo-backend \
        --type=merge \
        -p "{\"data\":{\"APP_URL\":\"https://$STATIC_DOMAIN\"}}" \
        && echo "      ConfigMap atualizado." \
        || echo "      (ConfigMap não encontrado — atualize manualmente)"
else
    echo "      oc não disponível — atualize APP_URL manualmente:"
    echo "      oc patch configmap ms-operacao-config -n fgo-backend \\"
    echo "         --type=merge -p '{\"data\":{\"APP_URL\":\"https://$STATIC_DOMAIN\"}}'"
fi

# =============================================================================
# Resultado
# =============================================================================
echo ""
echo "=============================================="
echo " ✅ ngrok configurado!"
echo "=============================================="
echo ""
echo " Links públicos (disponíveis agora):"
echo ""
echo "   Swagger UI:       https://$STATIC_DOMAIN/q/swagger-ui"
echo "   Health:           https://$STATIC_DOMAIN/q/health"
echo "   Métricas:         https://$STATIC_DOMAIN/admin/sistema"
echo "   Logs do pod:      https://$STATIC_DOMAIN/admin/logs"
echo "   API operações:    https://$STATIC_DOMAIN/api/agentes"
echo ""
echo " Comandos úteis:"
echo "   sudo systemctl status ngrok          # status do túnel"
echo "   sudo journalctl -u ngrok -f          # logs do túnel"
echo "   curl https://$STATIC_DOMAIN/q/health # teste rápido"
echo ""
echo " Dashboard ngrok (local):"
echo "   http://localhost:4040                # inspeciona requisições em tempo real"
echo ""
echo " ⚠️  Console OpenShift:"
echo "   O plano free do ngrok suporta apenas 1 domínio estático."
echo "   Para acessar o console, use:"
echo "     - oc CLI com o usuário fgo-viewer"
echo "     - Tailscale (se configurado)"
echo ""
echo " Cole no README os links acima."
