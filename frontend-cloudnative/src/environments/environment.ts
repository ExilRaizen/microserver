export const environment = {
  production: true,
  msalConfig: {
    auth: {
      clientId: '15dedad7-7204-44f7-9e0d-6589e6ced496',
      authority: 'https://login.microsoftonline.com/785588af-9f25-4dc1-a2d9-afbe56ee1787',
      redirectUri: 'http://localhost:4200'
    }
  },
  apiConfig: {
    pedidosApiUrl: 'http://localhost:8081',
    catalogoApiUrl: 'http://localhost:8082',
    soporteApiUrl: 'http://localhost:8083',
    pedidosScope: 'api://0e7d4bab-2455-4de8-bb75-c49e17617089/Pedidos.Create',
    catalogoScope: 'api://0e7d4bab-2455-4de8-bb75-c49e17617089/Catalogo.Manage',
    soporteScope: 'api://0e7d4bab-2455-4de8-bb75-c49e17617089/Soporte.Create'
  }
};
