const https = require('https');

https.get('https://api.stackexchange.com/2.3/questions/77227441/answers?order=desc&sort=activity&site=stackoverflow&filter=withbody', (res) => {
  let data = '';
  res.on('data', (chunk) => data += chunk);
  res.on('end', () => console.log(JSON.parse(data).items[0].body));
}).on('error', (e) => console.error(e));
