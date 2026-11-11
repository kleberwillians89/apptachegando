# Migrations anteriores — somente referência

Os cinco arquivos SQL foram movidos sem alterar seus bytes. Descrevem schemas incompatíveis
(UUID/TEXT, produtos normalizados/JSONB) e RPCs sem autorização suficiente.
Não os copie para a sequência ativa e não os aplique automaticamente a nenhum banco.
A sequência em `../migrations` é uma baseline para banco **novo e vazio**, não um upgrade
nem restauração dos dados antigos. Importação de banco legado exige plano próprio.
