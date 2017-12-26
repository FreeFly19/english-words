document.addEventListener('DOMContentLoaded', () => {
    const btn = document.getElementById('translate-button');

    btn.addEventListener('click', (e) => {
        const xhr = new XMLHttpRequest();

        xhr.open("PUT", "translate");
        xhr.setRequestHeader("Content-Type", "application/json");
        xhr.send(JSON.stringify({word: document.getElementById('translate-word').value}));
        xhr.onreadystatechange = function () {
            if (xhr.readyState !== 4) return;
            document.getElementById('translate-result').innerText =
                JSON.stringify(JSON.parse(xhr.responseText), null, 4);
        };
    });

});