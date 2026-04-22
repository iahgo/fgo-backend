#!/usr/bin/env bash
# =============================================================================
# setup-cloudflare-tunnel.sh — Expõe Swagger e Console OpenShift na internet
#                              via Cloudflare Tunnel (sem abrir porta no roteador)
#
# PRÉ-REQUISITOS:
#   1. Conta no Cloudflare (gratuita): https://dash.cloudflare.com/sign-up
#   2. Um domínio apontado para o Cloudflare (pode ser comprado lá mesmo por ~$10/ano)
#      ou um domínio gratuito em freenom.com transferido para o Cloudflare
#   3. Este script deve rodar como root no servidor RHEL
#
# O QUE FAZ:
#   - Instala cloudflared (daemon do Cloudflare Tunnel)
#   - Autentica com sua conta Cloudflare (abre browser uma vez)
#   - Cria um túnel permanente e registra no Cloudflare DNS automaticamente
#   - Expõe:
#       swagger.SEU-DOMINIO.com   → https://ms-operacao-fgo-backend.apps-crc.testing
#       openshift.SEU-DOMINIO.com → https://console-openshift-console.apps-crc.testing
#   - Instala como serviço systemd (inicia com o servidor)
#
# USO:
#   sudo bash setup-cloudflare-tunnel.sh SEU-DOMINIO.com
# =============================================================================

set -euo pipefail

DOMINIO="${1:-}"
TUNNEL_NAME="fgo-tunnel"

if [[ -z "$DOMINIO" ]]; then
    echo "Uso: sudo bash $0 SEU-DOMINIO.com"
    echo "Exemplo: sudo bash $0 meusite.com.br"
    exit 1
fi

if [[ $EUID -ne 0 ]]; then
    echo "Execute como root: sudo bash $0 $DOMINIO"
    exit 1
fi

echo "=============================================="
echo " Cloudflare Tunnel — FGO"
echo " Domínio: $DOMINIO"
echo "=============================================="

# =============================================================================
# 1. Instalar cloudflared
# =============================================================================
if ! command -v cloudflared &>/dev/null; then
    echo "[1/6] Instalando cloudflared..."
    # Repositório oficial Cloudflare para RHEL/CentOS/Fedora
    curl -fsSL https://pkg.cloudflare.com/cloudflare-main.repo \
        -o /etc/yum.repos.d/cloudflare.repo
    dnf install -y cloudflared
    echo "      cloudflared instalado: $(cloudflared --version)"
else
    echo "[1/6] cloudflared já instalado: $(cloudflared --version)"
fi

# =============================================================================
# 2. Autenticar com Cloudflare (abre browser — faça isso uma vez)
# =============================================================================
CERT_FILE="$HOME/.cloudflared/cert.pem"
if [[ ! -f "$CERT_FILE" ]]; then
    echo ""
    echo "[2/6] Autenticando com Cloudflare..."
    echo "      Um link será exibido — abra-o no navegador e autorize."
    echo "      Após autorizar, volte aqui."
    echo ""
    cloudflared tunnel login
else
    echo "[2/6] Já autenticado (cert.pem encontrado)."
fi

# =============================================================================
# 3. Criar o túnel
# =============================================================================
TUNNEL_ID=""
if cloudflared tunnel list 2>/dev/null | grep -q "$TUNNEL_NAME"; then
    echo "[3/6] Túnel '$TUNNEL_NAME' já existe."
    TUNNEL_ID=$(cloudflared tunnel list | grep "$TUNNEL_NAME" | awk '{print $1}')
else
    echo "[3/6] Criando túnel '$TUNNEL_NAME'..."
    cloudflared tunnel create "$TUNNEL_NAME"
    TUNNEL_ID=$(cloudflared tunnel list | grep "$TUNNEL_NAME" | awk '{print $1}')
fi
echo "      Tunnel ID: $TUNNEL_ID"

# =============================================================================
# 4. Criar o arquivo de configuração do túnel
# =============================================================================
CONFIG_DIR="/etc/cloudflared"
mkdir -p "$CONFIG_DIR"

# Encontra o arquivo de credenciais criado pelo 'tunnel create'
CRED_FILE="$HOME/.cloudflared/${TUNNEL_ID}.json"
if [[ ! -f "$CRED_FILE" ]]; then
    CRED_FILE="/root/.cloudflared/${TUNNEL_ID}.json"
fi

echo "[4/6] Criando configuração em $CONFIG_DIR/config.yml..."
cat > "$CONFIG_DIR/config.yml" <<EOF
tunnel: $TUNNEL_ID
credentials-file: $CRED_FILE

# Desabilita verificação TLS interna (OpenShift CRC usa certificado self-signed)
originRequest:
  noTLSVerify: true
  connectTimeout: 30s

ingress:
  # Swagger / API
  - hostname: swagger.$DOMINIO
    service: https://ms-operacao-fgo-backend.apps-crc.testing
    originRequest:
      noTLSVerify: true

  # Console OpenShift
  - hostname: openshift.$DOMINIO
    service: https://console-openshift-console.apps-crc.testing
    originRequest:
      noTLSVerify: true

  # API de métricas e logs (acesso direto sem Swagger)
  - hostname: api.$DOMINIO
    service: https://ms-operacao-fgo-backend.apps-crc.testing
    originRequest:
      noTLSVerify: true

  # Catch-all obrigatório
  - service: http_status:404
EOF

echo "      Configuração criada."

# =============================================================================
# 5. Criar registros DNS no Cloudflare automaticamente
# =============================================================================
echo "[5/6] Criando registros DNS no Cloudflare..."
for SUBDOMAIN in swagger openshift api; do
    cloudflared tunnel route dns "$TUNNEL_NAME" "$SUBDOMAIN.$DOMINIO" || true
    echo "      → $SUBDOMAIN.$DOMINIO"
done

# =============================================================================
# 6. Instalar e iniciar serviço systemd
# =============================================================================
echo "[6/6] Instalando serviço systemd..."
cloudflared service install

systemctl enable cloudflared
systemctl restart cloudflared
sleep 3

STATUS=$(systemctl is-active cloudflared)
echo "      Serviço cloudflared: $STATUS"

# =============================================================================
# Resultado
# =============================================================================
echo ""
echo "=============================================="
echo " ✅ Cloudflare Tunnel configurado!"
echo "=============================================="
echo ""
echo " Links públicos (disponíveis em ~30 segundos):"
echo ""
echo "   Swagger UI:          https://swagger.$DOMINIO/q/swagger-ui"
echo "   Console OpenShift:   https://openshift.$DOMINIO"
echo "   API (health):        https://api.$DOMINIO/q/health"
echo "   Métricas:            https://api.$DOMINIO/admin/sistema"
echo "   Logs do pod:         https://api.$DOMINIO/admin/logs"
echo ""
echo " Comandos úteis:"
echo "   sudo systemctl status cloudflared   # status do túnel"
echo "   sudo journalctl -u cloudflared -f   # logs do túnel"
echo "   cloudflared tunnel info $TUNNEL_NAME"
echo ""
echo " ⚠️  Atualize o application.properties e configmap com as URLs:"
echo "   APP_URL=https://api.$DOMINIO"
echo ""
