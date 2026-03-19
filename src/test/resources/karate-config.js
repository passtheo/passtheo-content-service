function fn() {
  var baseUrl = karate.properties['karate.baseUrl'] || 'http://localhost:8087';

  var config = {
    baseUrl: baseUrl,
    freeToken: 'test-bearer-free-user',
    paidToken: 'test-bearer-paid-user',
    freshToken: 'test-bearer-fresh-user'
  };

  karate.configure('connectTimeout', 15000);
  karate.configure('readTimeout', 15000);

  return config;
}
