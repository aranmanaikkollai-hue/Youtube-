const https = require('https');

https.get('https://api.stackexchange.com/2.3/questions/77227441/answers?order=desc&sort=activity&site=stackoverflow&filter=withbody', (res) => {
  let data = '';
  res.on('data', (chunk) => data += chunk);
  res.on('end', () => {
    const items = JSON.parse(data).items;
    if (items && items.length > 0) {
      console.log(items[0].body);
    } else {
      console.log('No answers found or error:', data);
    }
  });
}).on('error', (e) => console.error(e));
