import { useState, useEffect, useMemo, useCallback } from 'react';
import { MENU_TREE } from '../constants/menuTree';
import { fetchScreenDisplayLabelsSilent } from '../api/screenDisplayLabelsApi';
import { buildMergedMenuTree, applyLogTypeLabelOverrides } from '../utils/mergeMenuLabels';
import { hasEffectiveAppAccess } from '../utils/security';

const LOG_TYPE_BY_VIEW_DEFAULT = {
  'pb-feplog': { id: 'pb_feplog', name: 'PB FEP v1.0.0', description: '' },
  'pb-fep-log-search': { id: 'pb_feplog', name: 'PB FEP v2.0.0', description: '' },
  'java-fw-imagelog': { id: 'java_fw_imglog', name: 'Java FW Image Log', description: '' },
};

/**
 * After auth: GET screen labels once per session user; merge over defaults for sidebar and log titles.
 * GET failure: items [] (defaults only).
 */
export function useScreenDisplayLabels(isAuthenticated, user) {
  const [items, setItems] = useState([]);

  useEffect(() => {
    if (!isAuthenticated || !user || !hasEffectiveAppAccess(user)) {
      setItems([]);
      return undefined;
    }
    let cancelled = false;
    (async () => {
      const data = await fetchScreenDisplayLabelsSilent();
      if (!cancelled) setItems(Array.isArray(data) ? data : []);
    })();
    return () => {
      cancelled = true;
    };
    /* Intentional: fetch on auth + login identity change only; avoid refetch when parent merges user object. */
  }, [isAuthenticated, user?.username]); // eslint-disable-line react-hooks/exhaustive-deps

  const mergedMenuTree = useMemo(() => buildMergedMenuTree(MENU_TREE, items), [items]);

  const logTypesByView = useMemo(
    () => applyLogTypeLabelOverrides(LOG_TYPE_BY_VIEW_DEFAULT, items),
    [items]
  );

  const refresh = useCallback(async () => {
    const data = await fetchScreenDisplayLabelsSilent();
    setItems(Array.isArray(data) ? data : []);
  }, []);

  return {
    labelItems: items,
    setLabelItems: setItems,
    mergedMenuTree,
    /** Same as mergedMenuTree — presentation tree from buildMergedMenuTree (req 20260407). */
    menuTreeMerged: mergedMenuTree,
    logTypesByView,
    refresh,
    logTypeDefaults: LOG_TYPE_BY_VIEW_DEFAULT,
  };
}
