export const environment = {
  production: false,
  msalConfig: {
    auth: {
      clientId: '15dedad7-7204-44f7-9e0d-6589e6ced496',
      authority: 'https://login.microsoftonline.com/785588af-9f25-4dc1-a2d9-afbe56ee1787',
      redirectUri: 'http://localhost:4200'
    }
  },
  apiConfig: {
    pedidosApiUrl: 'https://jflrtvr9ch.execute-api.us-east-1.amazonaws.com',
    pedidosScope: 'api://0e7d4bab-2455-4de8-bb75-c49e17617089/Pedidos.Create',
    catalogoScope: 'api://0e7d4bab-2455-4de8-bb75-c49e17617089/Catalogo.Manage',
    soporteScope: 'api://0e7d4bab-2455-4de8-bb75-c49e17617089/Soporte.Create'
  }
};
