fetch('http://localhost:3001/api/auth/register', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    email: 'uitest@timo.dev',
    password: 'Test1234!',
    nickname: 'UITest',
    selfAssessedLevel: 'BEGINNER'
  })
}).then(r => r.text().then(t => console.log(r.status, t)))
  .catch(e => console.log('ERR', e.message))
