import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import SearchForm from './SearchForm';

describe('SearchForm (pb-fep-log-search wireframe)', () => {
  test('TC-03: combined start after end shows validation error and does not call onSearch', () => {
    const onSearch = jest.fn();
    render(<SearchForm onSearch={onSearch} />);

    fireEvent.change(screen.getByLabelText(/조회일자/i), { target: { value: '2025-01-15' } });
    fireEvent.change(screen.getByLabelText(/시작시간/i), { target: { value: '18:00' } });
    fireEvent.change(screen.getByLabelText(/종료시간/i), { target: { value: '09:00' } });

    fireEvent.change(screen.getByPlaceholderText('Login ID'), { target: { value: 'u1' } });
    fireEvent.click(screen.getByRole('button', { name: '검색' }));

    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(onSearch).not.toHaveBeenCalled();
  });
});
