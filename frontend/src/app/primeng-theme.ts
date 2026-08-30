import { definePreset } from '@primeng/themes';
import Aura from '@primeng/themes/aura';

// GENOMETRICS brand preset: deep green primary, warm orange accents,
// soft radii matching the existing --radius token family.
export const GenometricsPreset = definePreset(Aura, {
  semantic: {
    primary: {
      50: '#e8f5ef',
      100: '#cde9dd',
      200: '#a3d6c1',
      300: '#74bfa1',
      400: '#3f9a79',
      500: '#0f6a4d',
      600: '#0d5f45',
      700: '#0b503a',
      800: '#08402e',
      900: '#063122',
      950: '#032118'
    },
    colorScheme: {
      light: {
        primary: {
          color: '#0f6a4d',
          contrastColor: '#ffffff',
          hoverColor: '#0b503a',
          activeColor: '#08402e'
        },
        surface: {
          0: '#ffffff',
          50: '#f4f8f5',
          100: '#eef3f0',
          200: '#d9e7df',
          300: '#b7c9be',
          400: '#8ba394',
          500: '#4d6358',
          600: '#3d5046',
          700: '#2e3d35',
          800: '#1f2a24',
          900: '#13251c',
          950: '#0b1712'
        }
      }
    }
  },
  components: {
    card: {
      root: {
        borderRadius: '16px',
        shadow: '0 8px 24px rgba(19, 37, 28, 0.07)'
      }
    }
  }
});
