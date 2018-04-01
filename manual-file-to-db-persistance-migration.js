// Usage example:
// npm install pg
// node manual-file-to-db-persistance-migration.js /Users/freefly/Downloads/words.txt localhost freefly postgres ""
const fs = require('fs');
const { Client } = require('pg');

const client = new Client({
    host: process.argv[3],
    database: process.argv[4],
    user: process.argv[5],
    password: process.argv[5],
    port: 5432,
});

client.connect();

fs.readFile(process.argv[2], 'utf8', (err, data) => {
    if (err) throw err;

    const phrases = data.split('\n')
        .filter(p => p)
        .map(JSON.parse);

    const phrasesPromises = phrases
        .map(p => {
            return client.query(
                'INSERT INTO phrases(text, created_at) VALUES($1, $2) RETURNING *',
                [p.phrase, new Date(p.date)]
            ).then(res => {
                const phraseId = res.rows[0].id;

                const translatePromises = p.translates
                    .map(t => {
                        return client.query(
                            'INSERT INTO translations(phrase_id, value, picture, votes) VALUES($1, $2, $3, $4) RETURNING *',
                            [phraseId, t.value, t.pic_url, t.votes]
                        );
                    });

                return Promise.all(translatePromises);
            }, (err) => {
                console.error('Error: ' + err);
            });
        });

    Promise.all(phrasesPromises).then(() => client.end());
});

