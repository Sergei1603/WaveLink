import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { createCollection, deleteCollection, listCollections } from "../api/collections";
import type { CollectionSummary } from "../types";

export function CollectionsPage() {
  const [items, setItems] = useState<CollectionSummary[]>([]);
  const [name, setName] = useState("");
  const [err, setErr] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const reload = async () => {
    setLoading(true); setErr(null);
    try { setItems(await listCollections()); }
    catch (e: any) { setErr(e.message); }
    finally { setLoading(false); }
  };

  useEffect(() => { reload(); }, []);

  const create = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;
    try {
      const c = await createCollection(name.trim());
      setItems(prev => [c, ...prev]);
      setName("");
    } catch (e: any) { setErr(e.message); }
  };

  const remove = async (c: CollectionSummary) => {
    if (!confirm(`Удалить коллекцию «${c.name}»?`)) return;
    try { await deleteCollection(c.id); setItems(prev => prev.filter(x => x.id !== c.id)); }
    catch (e: any) { alert(e.message); }
  };

  return (
    <div className="page">
      <div className="page-header">
        <h1>Коллекции</h1>
      </div>

      <form className="inline-form" onSubmit={create}>
        <input
          placeholder="Название новой коллекции"
          value={name}
          onChange={e => setName(e.target.value)}
        />
        <button type="submit" className="btn-primary">Создать</button>
      </form>

      {err && <div className="error">{err}</div>}
      {loading && <div className="muted">Загрузка…</div>}
      {!loading && items.length === 0 && (
        <div className="empty">Коллекций ещё нет.</div>
      )}

      <div className="card-grid">
        {items.map(c => (
          <div key={c.id} className="card">
            <Link to={`/collections/${c.id}`} className="card-title">{c.name}</Link>
            <div className="muted">{c.trackCount} треков</div>
            <div className="card-actions">
              <button className="btn-danger" onClick={() => remove(c)}>Удалить</button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
