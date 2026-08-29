describe('GENOMETRICS smoke', () => {
  it('shows the app shell with sidebar', () => {
    cy.visit('/projects');
    cy.contains('GENOMETRICS');
    cy.contains('by Bruno Omena');
    cy.contains('Projects');
  });
});
