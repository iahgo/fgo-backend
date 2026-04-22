#!/usr/bin/env bash
# =============================================================================
# setup-tailscale-funnel.sh — Expõe Swagger e Console OpenShift na internet
#                             via Tailscale Funnel (gratuito, URL fixa)
#
# PRÉ-REQUISITO: Tailscale já instalado e autenticado no servidor RHEL.
#
# O QUE FAZ:
#   1. Descobre a URL pública do servidor no Tailscale (*.ts.net)
#   2. Instala nginx como proxy reverso (reescreve Host header para o OpenShift)
#   3. Ativa Tailscale Funnel (expõe para qualquer pessoa na internet)
#
# RESULTADO — URLs públicas fixas para sempre:
#   https://<hostname>.<tailnet>.ts.net/q/swagger-ui
#   https://<hostname>.<tailnet>.ts.net/q/health
#   https://<hostname>.<tailnet>.ts.net/admin/sistema
#   https://<hostname>.<tailnet>.ts.net/admin/logs
#   https://<hostname>.<tailnet>.ts.net/console   → Console OpenShift
#
# USO:
#   sudo bash setup-tailscale-funnel.sh
# =============================================================================

set -euo pipefail

if [[ $EUID -ne 0 ]]; then
    echo "Execute como root: sudo bash $0"
    exit 1
fi

echo "=============================================="
echo " Tailscale Funnel + nginx — FGO"
echo "=============================================="

# =============================================================================
# 1. Verifica Tailscale e descobre a URL pública
# =============================================================================
echo ""
echo "[1/5] Verificando Tailscale..."

if ! command -v tailscale &>/dev/null; then
    echo "ERRO: Tailscale não está instalado."
    echo "Instale em: https://tailscale.com/download/linux"
    exit 1
fi

if ! tailscale status &>/dev/null; then
    echo "ERRO: Tailscale não está conectado. Execute: tailscale up"
    exit 1
fi

# Pega o FQDN do nó no Tailscale (ex: servidor.tail1234.ts.net)
TS_FQDN=$(tailscale status --json 2>/dev/null | python3 -c \
    "import json,sys; d=json.load(sys.stdin); print(d['Self']['DNSName'].rstrip('.'))" \
    2>/dev/null || echo "")

if [[ -z "$TS_FQDN" ]]; then
    echo "ERRO: Não foi possível obter o FQDN do Tailscale."
    echo "Tente: tailscale status --json | python3 -c \"import json,sys; print(json.load(sys.stdin)['Self']['DNSName'])\""
    exit 1
fi

echo "      FQDN Tailscale: $TS_FQDN"

# Rotas OpenShift locais
ROUTE_API="ms-operacao-fgo-backend.apps-crc.testing"
ROUTE_CONSOLE="console-openshift-console.apps-crc.testing"
ROUTE_OAUTH="oauth-openshift.apps-crc.testing"

# =============================================================================
# 2. Ativa MagicDNS + HTTPS no Tailscale (necessário para Funnel)
# =============================================================================
echo ""
echo "[2/5] Habilitando HTTPS no Tailscale (necessário para Funnel)..."
tailscale cert "$TS_FQDN" 2>/dev/null || true
echo "      OK"

# =============================================================================
# 3. Instala e configura nginx
# =============================================================================
echo ""
echo "[3/5] Instalando nginx..."
if ! command -v nginx &>/dev/null; then
    dnf install -y nginx
fi

# Cria diretório de logs
mkdir -p /var/log/nginx

echo "      Criando configuração nginx em /etc/nginx/conf.d/fgo.conf..."
cat > /etc/nginx/conf.d/fgo.conf <<NGINX
# =============================================================================
# nginx — FGO proxy reverso para OpenShift CRC
# Recebe requisições do Tailscale Funnel (localhost:8080) e encaminha para
# as rotas do OpenShift com o Host header correto.
# =============================================================================

# Resolver DNS local (para resolver *.apps-crc.testing)
resolver 127.0.0.1 [::1] valid=10s;

server {
    listen 8080;
    server_name $TS_FQDN;

    # Logs de acesso com tempo de resposta
    access_log /var/log/nginx/fgo-access.log combined;
    error_log  /var/log/nginx/fgo-error.log warn;

    # Tamanho máximo do body (para o swagger enviar JSONs grandes)
    client_max_body_size 10m;

    # -------------------------------------------------------------------------
    # /console — Console OpenShift
    # -------------------------------------------------------------------------
    location /console {
        return 302 https://$TS_FQDN/console/;
    }
    location /console/ {
        proxy_pass          https://$ROUTE_CONSOLE/;
        proxy_ssl_verify    off;
        proxy_set_header    Host              $ROUTE_CONSOLE;
        proxy_set_header    X-Real-IP         \$remote_addr;
        proxy_set_header    X-Forwarded-For   \$proxy_add_x_forwarded_for;
        proxy_set_header    X-Forwarded-Proto https;
        proxy_http_version  1.1;
        proxy_set_header    Upgrade           \$http_upgrade;
        proxy_set_header    Connection        "upgrade";
        proxy_read_timeout  120s;

        # Reescreve URLs absolutas do console para passar pelo proxy
        sub_filter_types    *;
        sub_filter          '$ROUTE_CONSOLE'     '$TS_FQDN/console';
        sub_filter_once     off;
    }

    # -------------------------------------------------------------------------
    # /oauth — OAuth OpenShift (login do console)
    # -------------------------------------------------------------------------
    location /oauth/ {
        proxy_pass          https://$ROUTE_OAUTH/oauth/;
        proxy_ssl_verify    off;
        proxy_set_header    Host              $ROUTE_OAUTH;
        proxy_set_header    X-Real-IP         \$remote_addr;
        proxy_set_header    X-Forwarded-For   \$proxy_add_x_forwarded_for;
        proxy_set_header    X-Forwarded-Proto https;
        proxy_http_version  1.1;
        proxy_read_timeout  120s;
    }

    # -------------------------------------------------------------------------
    # Tudo mais → API (Swagger, operações, admin, health)
    # -------------------------------------------------------------------------
    location / {
        proxy_pass          https://$ROUTE_API;
        proxy_ssl_verify    off;
        proxy_set_header    Host              $ROUTE_API;
        proxy_set_header    X-Real-IP         \$remote_addr;
        proxy_set_header    X-Forwarded-For   \$proxy_add_x_forwarded_for;
        proxy_set_header    X-Forwarded-Proto https;
        proxy_http_version  1.1;
        proxy_set_header    Upgrade           \$http_upgrade;
        proxy_set_header    Connection        "upgrade";
        proxy_read_timeout  300s;  # queries pesadas do /consolidado podem demorar
        proxy_buffering     off;   # streaming de logs em tempo real
    }
}
NGINX

# Remove config default que pode conflitar
rm -f /etc/nginx/conf.d/default.conf

# Testa configuração
nginx -t

# Abre porta 8080 no firewall local (nginx escuta aqui, só localhost + funnel)
firewall-cmd --add-port=8080/tcp --permanent 2>/dev/null || true
firewall-cmd --reload 2>/dev/null || true

systemctl enable nginx
systemctl restart nginx
echo "      nginx rodando na porta 8080."

# =============================================================================
# 4. Ativa Tailscale Funnel
# =============================================================================
echo ""
echo "[4/5] Ativando Tailscale Funnel na porta 443 → localhost:8080..."

# Funnel precisa estar habilitado na conta: https://login.tailscale.com/admin/dns
# Ativa Funnel: requisições externas (internet) na 443 → nginx local na 8080
tailscale funnel --bg 443

# Verifica se o serve está configurado para rotear 443 → 8080
tailscale serve --bg https://localhost:8080 2>/dev/null || \
tailscale serve --bg http://localhost:8080 2>/dev/null || true

# Configura corretamente via serve + funnel
tailscale serve reset 2>/dev/null || true
tailscale serve --bg --https=443 localhost:8080
tailscale funnel --bg --https=443 on

echo "      Funnel ativado."

# =============================================================================
# 5. Atualiza ConfigMap do OpenShift
# =============================================================================
echo ""
echo "[5/5] Atualizando APP_URL no ConfigMap do OpenShift..."
if command -v oc &>/dev/null && oc whoami &>/dev/null 2>&1; then
    oc patch configmap ms-operacao-config -n fgo-backend \
        --type=merge \
        -p "{\"data\":{\"APP_URL\":\"https://${TS_FQDN}\"}}" \
        && echo "      ConfigMap atualizado — Swagger vai usar URL pública." \
        || echo "      (falha ao atualizar ConfigMap — faça manualmente)"
else
    echo "      oc não disponível neste contexto."
    echo "      Execute manualmente:"
    echo "      oc patch configmap ms-operacao-config -n fgo-backend \\"
    echo "         --type=merge -p '{\"data\":{\"APP_URL\":\"https://${TS_FQDN}\"}}'"
fi

# =============================================================================
# Resultado
# =============================================================================
echo ""
echo "=============================================="
echo " ✅ Tailscale Funnel configurado!"
echo "=============================================="
echo ""
echo " URLs públicas (acessíveis por qualquer pessoa na internet):"
echo ""
echo "   Swagger UI:       https://$TS_FQDN/q/swagger-ui"
echo "   Health:           https://$TS_FQDN/q/health"
echo "   Métricas:         https://$TS_FQDN/admin/sistema"
echo "   Logs do pod:      https://$TS_FQDN/admin/logs"
echo "   Console OpenShift: https://$TS_FQDN/console"
echo ""
echo " Comandos úteis:"
echo "   tailscale funnel status        # status do funnel"
echo "   tailscale serve status         # o que está sendo exposto"
echo "   systemctl status nginx         # status do proxy"
echo "   tail -f /var/log/nginx/fgo-access.log  # logs de acesso"
echo ""
echo " ⚠️  Se o Funnel falhar com 'feature not enabled':"
echo "   1. Acesse: https://login.tailscale.com/admin/dns"
echo "   2. Ative 'HTTPS Certificates'"
echo "   3. Acesse: https://login.tailscale.com/admin/acls"
echo "   4. Adicione em 'nodeAttrs': {\"target\":[\"*\"],\"attr\":[\"funnel\"]}"
echo "   5. Rode este script novamente."
echo ""
echo " Para forçar restart do nginx após redeploy:"
echo "   sudo systemctl restart nginx"
