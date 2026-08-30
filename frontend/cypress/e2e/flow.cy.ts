const project = {
  id: 'p1',
  name: 'Cohort',
  description: 'panel',
  createdAt: '2026-01-01T00:00:00Z'
};

const sample = {
  id: 's1',
  projectId: 'p1',
  name: 'S1',
  notes: '',
  fastaReferenceName: null,
  fastaStoragePath: null,
  createdAt: '2026-01-01T00:00:00Z'
};

const uploadedFile = {
  id: 'f1',
  originalFilename: 'a.fastq',
  fileType: 'FASTQ',
  sizeBytes: 16,
  uploadedAt: project.createdAt
};

const queuedAnalysis = {
  id: 'a1',
  fileAssetId: 'f1',
  status: 'QUEUED',
  engine: 'JAVA',
  createdAt: project.createdAt
};

describe('project flow', () => {
  it('creates a project, sample, and uploads a file', () => {
    cy.intercept('GET', '**/api/projects', []).as('emptyProjects');
    cy.visit('/projects');
    cy.wait('@emptyProjects');

    cy.intercept('POST', '**/api/projects', { statusCode: 200, body: project }).as('createProject');
    cy.intercept('GET', '**/api/projects', [project]).as('projects');
    cy.get('input[name=name]').type('Cohort');
    cy.contains('button', 'Create').click();
    cy.wait('@createProject');
    cy.contains('Cohort');

    cy.intercept('GET', '**/api/projects/p1', project).as('project');
    cy.intercept('GET', '**/api/projects/p1/samples', []).as('emptySamples');
    cy.contains('a', 'Cohort').click();
    cy.wait('@project');
    cy.wait('@emptySamples');

    cy.intercept('POST', '**/api/projects/p1/samples', sample).as('createSample');
    cy.intercept('GET', '**/api/projects/p1/samples', [sample]).as('samples');
    cy.get('input[name=sampleName]').type('S1');
    cy.contains('button', 'Add sample').click();
    cy.wait('@createSample');
    cy.contains('S1');

    cy.intercept('GET', '**/api/samples/s1/files', []).as('files');
    cy.intercept('GET', '**/api/samples/s1/analyses', []).as('analyses');
    cy.intercept('GET', '**/api/samples/s1/metrics/fastq', { statusCode: 404, body: {} });
    cy.intercept('GET', '**/api/samples/s1/metrics/vcf', { statusCode: 404, body: {} });
    cy.contains('a', 'S1').click();
    cy.contains('Upload & analyze');

    cy.intercept('GET', '**/api/samples/s1/files', [uploadedFile]).as('filesAfterUpload');
    cy.intercept('GET', '**/api/samples/s1/analyses', [queuedAnalysis]).as('analysesAfterUpload');
    cy.intercept('POST', '**/api/samples/s1/files', {
      statusCode: 200,
      body: { file: uploadedFile, analysis: queuedAnalysis }
    }).as('upload');
    cy.get('input[type=file]').selectFile(
      { contents: Cypress.Buffer.from('@r1\nACGT\n+\nIIII\n'), fileName: 'a.fastq', mimeType: 'text/plain' },
      { force: true }
    );
    cy.contains('button', 'Upload & analyze').click();
    cy.wait('@upload');
    cy.contains('File uploaded — analysis queued');
    cy.contains('QUEUED');
  });
});
