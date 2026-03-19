function fn() {
  var env = karate.env || 'local';
  karate.log('karate.env =', env);

  // Use dynamic baseUrl set by KarateRunner via System.setProperty, fallback to default
  var baseUrl = karate.properties['karate.baseUrl'] || 'http://localhost:8087';

  if (env === 'docker') {
    baseUrl = 'http://passtheo-content-service:8087';
  }

  var config = {
    baseUrl: baseUrl
  };

  // Dummy Bearer tokens — security is permissive in acceptance tests (TestSecurityConfig)
  // Real token validation is handled by the API Gateway in production
  config.freeToken = 'test-bearer-free-user';
  config.paidToken = 'test-bearer-paid-user';
  config.freshToken = 'test-bearer-fresh-user';

  karate.log('karate.baseUrl =', config.baseUrl);

  return config;
}
