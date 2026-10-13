describe('GENOMETRICS smoke', () => {
  it('shows the app shell with sidebar', () => {
    cy.visit('/projects');
    cy.contains('GENOMETRICS');
    cy.contains('by Bruno Omena');
    cy.contains('Projects');
    cy.contains('a', 'Explore');
  });

  it('opens Explore and runs a grounded analytics query', () => {
    cy.intercept('GET', '**/api/analytics/metrics', [{
      key: 'mean_quality',
      label: 'Mean quality',
      domain: 'FASTQ',
      unit: 'Phred',
      description: 'Mean Phred quality score',
      aggregations: ['AVG'],
      version: 1
    }]);
    cy.intercept('GET', '**/api/projects', []);
    cy.intercept('POST', '**/api/analytics/query', {
      columns: ['sample', 'metric', 'value'],
      rows: [{ sample: 'S1', metric: 'mean_quality', value: 31.5 }],
      total: 1,
      truncated: false
    }).as('analyticsQuery');

    cy.visit('/explore');
    cy.contains('Explore');
    cy.get('#metrics option').should('contain', 'Mean quality');
    cy.contains('button', 'Run query').should('be.enabled').click();
    cy.wait('@analyticsQuery');
    cy.contains('31.5');
  });
});
