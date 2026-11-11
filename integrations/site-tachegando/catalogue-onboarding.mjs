/** TáChegando web onboarding adapter. No credentials or browser storage. */
export const VENDOR_CATEGORIES = Object.freeze([
  'Pães', 'Frutas', 'Salgados', 'Doces de padaria', 'Lanches naturais',
  'Sorvetes', 'Temperos', 'Produtos de limpeza prontos'
]);

export function validateVendorCatalogue(form) {
  const names = Array.isArray(form?.products) ? form.products.map(p => typeof p === 'string' ? p.trim() : '') : [];
  const uniqueNames = new Set(names.map(n => n.toLocaleLowerCase('pt-BR')));
  if (typeof form?.name !== 'string' || !form.name.trim() || form.name.trim().length > 160 ||
      typeof form?.businessName !== 'string' || !form.businessName.trim() || form.businessName.trim().length > 160 ||
      !VENDOR_CATEGORIES.includes(form?.category) ||
      names.length < 1 || names.length > 30 ||
      names.some(n => !n || n.length > 160) || uniqueNames.size !== names.length ||
      (form?.phone != null && (typeof form.phone !== 'string' || form.phone.length > 32))) {
    throw new Error('Confira os dados do negócio e informe entre 1 e 30 produtos diferentes.');
  }
  return {
    p_name: form.name.trim(), p_business_name: form.businessName.trim(),
    p_category: form.category, p_product_names: names, p_phone: form.phone?.trim() || null
  };
}

/** Call only after Supabase Auth confirmation/login. Does not persist drafts or passwords. */
export async function completeVendorCatalogue(supabase, form) {
  const params = validateVendorCatalogue(form);
  if (!supabase?.auth?.getUser || !supabase?.rpc) throw new Error('Supabase indisponível.');
  const { data: identity, error: identityError } = await supabase.auth.getUser();
  if (identityError || !identity?.user?.id || !identity.user.email_confirmed_at) {
    throw new Error('Confirme seu e-mail e entre na conta antes de finalizar o cadastro.');
  }
  const { data, error } = await supabase.rpc('complete_vendor_onboarding', params);
  if (error) throw new Error('Não foi possível salvar o catálogo. Tente novamente.');
  if (!data?.profile?.vendor_id || !Array.isArray(data.products)) {
    throw new Error('O cadastro não foi confirmado pelo servidor.');
  }
  return data;
}
