const authBody = {
  token: 'jwt-token',
  email: 'a@b.com',
  displayName: 'Ana',
  role: 'RESEARCHER',
  userId: 'u1'
};

describe('auth', () => {
  it('shows error on 401', () => {
    cy.intercept('POST', '**/api/auth/login', {
      statusCode: 401,
      body: { message: 'Invalid credentials' }
    }).as('login');
    cy.visit('/login');
    cy.get('input[name=email]').type('a@b.com');
    cy.get('input[name=password]').type('wrong');
    cy.contains('button', 'Sign in').click();
    cy.wait('@login');
    cy.contains('Invalid credentials');
    cy.url().should('include', '/login');
  });

  it('goes to projects after a successful login', () => {
    cy.intercept('POST', '**/api/auth/login', { statusCode: 200, body: authBody }).as('login');
    cy.intercept('GET', '**/api/projects', []).as('projects');
    cy.visit('/login');
    cy.get('input[name=email]').type('a@b.com');
    cy.get('input[name=password]').type('secret12');
    cy.contains('button', 'Sign in').click();
    cy.wait('@login');
    cy.url().should('include', '/projects');
    cy.contains('Projects');
  });
});
