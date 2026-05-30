import { useNavigate } from 'react-router-dom';

const TABS = [
  { key: 'home',      path: '/',          icon: 'fa-solid fa-house',             label: 'Home'     },
  { key: 'tags',      path: '/tags',      icon: 'fa-solid fa-tags',              label: 'Tags'     },
  { key: 'notebooks', path: '/notebooks', icon: 'fa-solid fa-book-open',         label: 'Notebook' },
];

export default function BottomNav({ active }) {
  const navigate = useNavigate();

  return (
    <nav className="bottom-nav" aria-label="Bottom navigation">
      {TABS.map(tab => (
        <a
          key={tab.key}
          className={`nav-item${active === tab.key ? ' active' : ''}`}
          onClick={() => navigate(tab.path)}
          role="button"
          tabIndex={0}
          onKeyDown={e => e.key === 'Enter' && navigate(tab.path)}
          aria-label={tab.label}
        >
          <i className={tab.icon} />
          <span>{tab.label}</span>
          <b />
        </a>
      ))}
    </nav>
  );
}
