import { useState, useCallback } from 'react';

/**
 * Shared sort state hook. Returns [sortConfig, handleSort] for use with DataTable.
 * sortConfig: { key: string | null, direction: 'asc' | 'desc' }
 * handleSort(key): toggles direction when same key, else sets key and direction 'asc'.
 */
export function useSortConfig(initialKey = null, initialDirection = 'asc') {
  const [sortConfig, setSortConfig] = useState({
    key: initialKey,
    direction: initialDirection,
  });

  const handleSort = useCallback((key) => {
    setSortConfig((prev) => {
      if (prev.key === key) {
        return { key, direction: prev.direction === 'asc' ? 'desc' : 'asc' };
      }
      return { key, direction: 'asc' };
    });
  }, []);

  return [sortConfig, handleSort];
}

export default useSortConfig;
