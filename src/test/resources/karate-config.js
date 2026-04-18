function fn() {
  var baseUrl = karate.properties['karate.baseUrl'] || 'http://localhost:8087';

  var now = new Date();
  now.setDate(now.getDate() + 30);
  var futureDate = now.toISOString().split('T')[0];
  var far = new Date();
  far.setDate(far.getDate() + 60);
  var farFutureDate = far.toISOString().split('T')[0];

  var config = {
    baseUrl: baseUrl,
    tenantId: '11111111-1111-1111-1111-111111111111',
    userId: '33333333-3333-3333-3333-333333333333',
    userWithoutExamDate: '22222222-2222-2222-2222-222222222222',
    learnerRole: 'LEARNER',
    futureDate: futureDate,
    farFutureDate: farFutureDate,
    freeToken: 'test-bearer-free-user',
    paidToken: 'test-bearer-paid-user',
    freshToken: 'test-bearer-fresh-user'
  };

  karate.configure('connectTimeout', 15000);
  karate.configure('readTimeout', 15000);
  karate.configure('followRedirects', false);

  return config;
}
