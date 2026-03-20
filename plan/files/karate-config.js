function fn() {
  var env = karate.env || 'local';
  karate.log('karate.env =', env);

  var config = {
    baseUrl: 'http://localhost:8087'
  };

  if (env === 'docker') {
    config.baseUrl = 'http://passtheo-content-service:8087';
  }

  var JwtFactory = Java.type('com.passtheo.shared.testing.JwtFactory');
  var tenantId = '11111111-1111-1111-1111-111111111111';

  // Free user — limited access
  config.freeToken = JwtFactory.createToken({
    sub: '22222222-2222-2222-2222-222222222222',
    tenantId: tenantId,
    roles: ['LEARNER']
  });

  // Paid user — active MONTH_1 subscription, has study history
  config.paidToken = JwtFactory.createToken({
    sub: '33333333-3333-3333-3333-333333333333',
    tenantId: tenantId,
    roles: ['LEARNER']
  });

  // Fresh user — no history at all
  config.freshToken = JwtFactory.createToken({
    sub: '77777777-7777-7777-7777-777777777777',
    tenantId: tenantId,
    roles: ['LEARNER']
  });

  return config;
}
