import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ImageLogSearchForm from './ImageLogSearchForm';

jest.mock('../utils/logger', () => ({
  debug: jest.fn(),
  info: jest.fn(),
  error: jest.fn(),
}));

describe('ImageLogSearchForm (req 20260318 image log search data/header/keyword fix)', () => {
  const onSearch = jest.fn();

  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('TC-06: Form submit payload includes datastring, headerstring, keywords (array) with correct keys', () => {
    test('onSearch receives object with datastring, headerstring, keywords (array) when fields are filled', async () => {
      render(<ImageLogSearchForm onSearch={onSearch} />);

      const datastringInput = screen.getByLabelText(/데이터/);
      const headerstringInput = screen.getByLabelText(/헤더/);
      const keywordsInput = screen.getByLabelText(/키워드 검색/);

      await userEvent.type(datastringInput, 'data-value');
      await userEvent.type(headerstringInput, 'header-value');
      await userEvent.type(keywordsInput, 'kw1, kw2, kw3');

      const submitButton = screen.getByRole('button', { name: '검색' });
      await userEvent.click(submitButton);

      expect(onSearch).toHaveBeenCalledTimes(1);
      const payload = onSearch.mock.calls[0][0];
      expect(payload).toHaveProperty('datastring', 'data-value');
      expect(payload).toHaveProperty('headerstring', 'header-value');
      expect(payload).toHaveProperty('keywords');
      expect(Array.isArray(payload.keywords)).toBe(true);
      expect(payload.keywords).toEqual(['kw1', 'kw2', 'kw3']);
    });

    test('onSearch receives empty string for datastring/headerstring and empty array for keywords when fields are empty', async () => {
      render(<ImageLogSearchForm onSearch={onSearch} />);
      const submitButton = screen.getByRole('button', { name: '검색' });
      await userEvent.click(submitButton);

      expect(onSearch).toHaveBeenCalledTimes(1);
      const payload = onSearch.mock.calls[0][0];
      expect(payload).toHaveProperty('datastring', '');
      expect(payload).toHaveProperty('headerstring', '');
      expect(payload).toHaveProperty('keywords');
      expect(Array.isArray(payload.keywords)).toBe(true);
      expect(payload.keywords).toEqual([]);
    });

    test('payload includes decryptData false for API compatibility', async () => {
      render(<ImageLogSearchForm onSearch={onSearch} />);
      await userEvent.click(screen.getByRole('button', { name: '검색' }));

      const payload = onSearch.mock.calls[0][0];
      expect(payload).toHaveProperty('decryptData', false);
      expect(typeof payload.decryptData).toBe('boolean');
    });
  });
});
