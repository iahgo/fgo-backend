#!/usr/bin/env bash
# =============================================================================
# setup-openshift-users.sh — Cria usuários reais no OpenShift via HTPasswd
#
# O OpenShift CRC vem com apenas 'kubeadmin' (senha temporária).
# Este script configura o provedor HTPasswd e cria perfis de usuário
# com diferentes níveis de acesso.
#
# USUÁRIOS CRIADOS:
#   fgo-admin     (senha: FgoAdmin2026!)  → cluster-admin — acesso total
#   fgo-dev       (senha: FgoDev2026!)   → edit em fgo-backend — deploy e logs
#   fgo-viewer    (senha: FgoView2026!)  → view em fgo-backend — somente leitura
#   log-viewer    (senha: FgoLog2026!)   → view em fgo-backend — igual fgo-viewer
#
# USO:
#   Execute no servidor RHEL como usuário 'fgo' (ou qualquer um com oc login feito):
#   bash setup-openshift-users.sh
#
# PRÉ-REQUISITO:
#   oc login --server=https://localhost:6443 --token=<kubeadmin-token>
# =============================================================================

set -euo pipefail

echo "=============================================="
echo " OpenShift — Configuração de usuários HTPasswd"
echo "=============================================="

# =============================================================================
# Verifica conectividade com o cluster
# =============================================================================
if ! oc whoami &>/dev/null; then
    echo "ERRO: Não está autenticado no OpenShift."
    echo "Execute: oc login --server=https://localhost:6443 -u kubeadmin"
    exit 1
fi
echo "Autenticado como: $(oc whoami)"

# =============================================================================
# Instala htpasswd se necessário
# =============================================================================
if ! command -v htpasswd &>/dev/null; then
    echo "Instalando httpd-tools (htpasswd)..."
    sudo dnf install -y httpd-tools
fi

# =============================================================================
# Cria o arquivo HTPasswd com todos os usuários
# =============================================================================
HTPASSWD_FILE="/tmp/fgo-htpasswd"
rm -f "$HTPASSWD_FILE"

declare -A USUARIOS=(
    ["fgo-admin"]="FgoAdmin2026!"
    ["fgo-dev"]="FgoDev2026!"
    ["fgo-viewer"]="FgoView2026!"
    ["log-viewer"]="FgoLog2026!"
)

echo ""
echo "[1/4] Criando senhas (HTPasswd bcrypt)..."
for USER in "${!USUARIOS[@]}"; do
    SENHA="${USUARIOS[$USER]}"
    htpasswd -bBc "$HTPASSWD_FILE" "$USER" "$SENHA" 2>/dev/null || \
    htpasswd -bB  "$HTPASSWD_FILE" "$USER" "$SENHA"
    echo "      $USER → senha definida"
done

# =============================================================================
# Cria/atualiza o Secret no OpenShift com o arquivo HTPasswd
# =============================================================================
echo ""
echo "[2/4] Aplicando Secret htpasswd no OpenShift..."
oc create secret generic fgo-htpasswd \
    --from-file=htpasswd="$HTPASSWD_FILE" \
    -n openshift-config \
    --dry-run=client -o yaml | oc apply -f -

# =============================================================================
# Configura o OAuth do cluster para usar HTPasswd
# =============================================================================
echo ""
echo "[3/4] Configurando OAuth provider (HTPasswd)..."

# Patch no OAuth cluster — adiciona o provider sem remover o kubeadmin
oc patch oauth cluster --type=merge -p '{
  "spec": {
    "identityProviders": [
      {
        "name": "fgo-htpasswd",
        "mappingMethod": "claim",
        "type": "HTPasswd",
        "htpasswd": {
          "fileData": {
            "name": "fgo-htpasswd"
          }
        }
      }
    ]
  }
}'

echo "      OAuth configurado. Os pods de autenticação vão reiniciar (~2min)."

# =============================================================================
# Aplica RoleBindings
# =============================================================================
echo ""
echo "[4/4] Aplicando permissões..."

# fgo-admin → cluster-admin (acesso total ao cluster)
oc adm policy add-cluster-role-to-user cluster-admin fgo-admin || true

# fgo-dev → edit no namespace fgo-backend (pode fazer deploy, ver logs, escalar)
oc adm policy add-role-to-user edit fgo-dev -n fgo-backend || true

# fgo-viewer e log-viewer → view no namespace fgo-backend (somente leitura)
oc adm policy add-role-to-user view fgo-viewer  -n fgo-backend || true
oc adm policy add-role-to-user view log-viewer  -n fgo-backend || true

echo "      Permissões aplicadas."

# Limpa arquivo temporário
rm -f "$HTPASSWD_FILE"

# =============================================================================
# Resultado
# =============================================================================
echo ""
echo "=============================================="
echo " ✅ Usuários configurados!"
echo "=============================================="
echo ""
echo " Aguarde ~2 minutos para o OAuth reiniciar, depois:"
echo ""
echo " ┌─────────────┬──────────────────┬─────────────────────────────────────┐"
echo " │ Usuário     │ Senha            │ Acesso                              │"
echo " ├─────────────┼──────────────────┼─────────────────────────────────────┤"
echo " │ fgo-admin   │ FgoAdmin2026!    │ Cluster-admin (acesso total)        │"
echo " │ fgo-dev     │ FgoDev2026!      │ Edit em fgo-backend (deploy + logs) │"
echo " │ fgo-viewer  │ FgoView2026!     │ View em fgo-backend (somente leitura│"
echo " │ log-viewer  │ FgoLog2026!      │ View em fgo-backend (logs e pods)   │"
echo " └─────────────┴──────────────────┴─────────────────────────────────────┘"
echo ""
echo " Login via CLI:"
echo "   oc login --server=https://localhost:6443 -u fgo-viewer -p 'FgoView2026!'"
echo ""
echo " Login via Console (URL local):"
echo "   https://console-openshift-console.apps-crc.testing"
echo "   → Selecione: fgo-htpasswd → Entre com usuário e senha acima"
echo ""
echo " Login via Console (URL pública, após setup-cloudflare-tunnel.sh):"
echo "   https://openshift.SEU-DOMINIO.com"
echo ""
echo " ⚠️  Anote as senhas acima — elas não ficam armazenadas em texto simples."
echo "     Para trocar uma senha:"
echo "     htpasswd -bB /tmp/fgo-htpasswd fgo-viewer NovaSenha"
echo "     oc create secret generic fgo-htpasswd --from-file=htpasswd=/tmp/fgo-htpasswd"
echo "     -n openshift-config --dry-run=client -o yaml | oc apply -f -"
