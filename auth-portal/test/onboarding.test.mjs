import test from 'node:test';
import assert from 'node:assert/strict';
import { vendorOnboardingDraft } from '../src/onboarding.mjs';

const user = {
  id:'user-test', email_confirmed_at:'2026-11-11T00:00:00Z',
  user_metadata:{role:'vendor',name:'Ana',business_name:'Pães da Ana',category:'Pães',
    onboarding_products:['Pão francês','Sonho']}
};
test('confirmed vendor produces normalized, non-authoritative draft',()=>{
  assert.deepEqual(vendorOnboardingDraft(user),{params:{
    p_name:'Ana',p_business_name:'Pães da Ana',p_category:'Pães',
    p_product_names:['Pão francês','Sonho'],p_phone:null
  }});
});
test('consumer never triggers vendor provisioning',()=>{
  assert.equal(vendorOnboardingDraft({...user,user_metadata:{...user.user_metadata,role:'consumer'}}),null);
});
test('unconfirmed vendor requires completion, never assumes verified',()=>{
  assert.deepEqual(vendorOnboardingDraft({...user,email_confirmed_at:null}),{needsCompletion:true});
});
test('duplicate, empty, invalid category inputs cannot pass',()=>{
  assert.deepEqual(vendorOnboardingDraft({...user,user_metadata:{...user.user_metadata,onboarding_products:['Pão',' pão ']}}),{needsCompletion:true});
  assert.deepEqual(vendorOnboardingDraft({...user,user_metadata:{...user.user_metadata,category:'Outro'}}),{needsCompletion:true});
  assert.deepEqual(vendorOnboardingDraft({...user,user_metadata:{...user.user_metadata,onboarding_products:[]}}),{needsCompletion:true});
});
