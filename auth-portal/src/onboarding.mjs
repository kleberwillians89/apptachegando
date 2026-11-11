/** Untrusted signup metadata is only an onboarding draft. The RPC validates authorization. */
export function vendorOnboardingDraft(user) {
  const m = user?.user_metadata;
  if (m?.role !== 'vendor') return null;
  const products = Array.isArray(m.onboarding_products) ? m.onboarding_products : [];
  const category = m.category;
  const allowed = ['Pães','Frutas','Salgados','Doces de padaria','Lanches naturais','Sorvetes','Temperos','Produtos de limpeza prontos'];
  if (!user?.id || !user.email_confirmed_at ||
      typeof m.name !== 'string' || !m.name.trim() || m.name.trim().length > 160 ||
      typeof m.business_name !== 'string' || !m.business_name.trim() || m.business_name.trim().length > 160 ||
      !allowed.includes(category) || products.length < 1 || products.length > 30 ||
      products.some(x=>typeof x !== 'string' || !x.trim() || x.trim().length > 160) ||
      new Set(products.map(x=>x.trim().toLocaleLowerCase('pt-BR'))).size !== products.length) {
    return {needsCompletion:true};
  }
  return {params:{
    p_name:m.name.trim(), p_business_name:m.business_name.trim(),p_category:category,
    p_product_names:products.map(x=>x.trim()),
    p_phone:typeof m.phone==='string' && m.phone.length <= 32 ? m.phone.trim() || null : null
  }};
}
