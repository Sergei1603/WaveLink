import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { createCollection, deleteCollection } from "../api/collections";
import { useAppShell } from "../app/AppShellContext";
import type { CollectionSummary } from "../types";

export function CollectionsPage() {
  const { collections, refreshCollections } = useAppShell();
  const [name, setName] = useState("");
  const [err, setErr] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => { await refreshCollections(); setLoading(false); })();
  }, [refreshCollections]);

  const create = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;
    setErr(null);
    try {
      await createCollection(name.trim());
      setName("");
      await refreshCollections();
    } catch (e: any) { setErr(e.message); }
  };

  const remove = async (c: CollectionSummary) => {
    if (!confirm(`Удалить коллекцию «${c.name}»?`)) return;
    try { await deleteCollection(c.id); await refreshCollections(); }
    catch (e: any) { alert(e.message); }
  };

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Коллекции</h1>
          <div className="page-sub">
            Свои подборки из треков библиотеки — они же видны в правой колонке.
          </div>
        </div>
      </div>

      <form className="inline-form" onSubmit={create}>
        <input
          className="input"
          placeholder="Название новой коллекции"
          value={name}
          onChange={e => setName(e.target.value)}
        />
        <button type="submit" className="btn btn-primary">Создать</button>
      </form>

      {err && <div className="error">{err}</div>}
      {loading && <div className="muted">Загрузка…</div>}
      {!loading && collections.length === 0 && (
        <div className="empty">Коллекций ещё нет.</div>
      )}

      <div className="card-grid">
        {collections.map(c => (
          <div key={c.id} className="card elev-sm">
            <Link to={`/collections/${c.id}`} className="card-title">{c.name}</Link>
            <div className="muted small">{c.trackCount} треков</div>
            <div className="card-actions">
              <button className="btn btn-danger btn-sm" onClick={() => remove(c)}>Удалить</button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
