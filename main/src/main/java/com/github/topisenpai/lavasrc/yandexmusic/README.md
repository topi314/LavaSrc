## How to get accessToken
1. (Optional) Open DevTools in your browser and on the Network tab enable trotlining.
2. Go to [https://oauth.yandex.ru/authorize?response_type=accessToken&client_id=23cabbbdc6cd418abb4b39c32c41195d](https://oauth.yandex.ru/authorize?response_type=accessToken&client_id=23cabbbdc6cd418abb4b39c32c41195d)
3. Authorize and grant access
4. The browser will redirect to the address like `https://music.yandex.ru/#access_token=AQAAAAAYc***&token_type=bearer&expires_in=31535645`.
   Very quickly there will be a redirect to another page, so you need to have time to copy the link. ![image](https://user-images.githubusercontent.com/68972811/196124196-a817b828-3387-4f70-a2b2-cdfdc71ce1f2.png)
5. Your accessToken, what is after `access_token`.

Token expires in 1 year. You can get a new one by repeating the steps above.

## Important information
Yandex Music is very location-dependent. You should either have a premium subscription or be located in one of the following countries:
- Azerbaijan
- Armenia
- Belarus
- Georgia
- Kazakhstan
- Kyrgyzstan
- Moldova
- Russia
- Tajikistan
- Turkmenistan
- Uzbekistan

Else you will only have access to podcasts.