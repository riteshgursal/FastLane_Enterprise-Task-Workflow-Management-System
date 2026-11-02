import { Link } from 'react-router-dom';
import { Empty } from '../components/ui';

export function NotFoundPage() {
  return <Empty title="Page not found" body="The link may be broken, or the page was moved." action={<Link to="/" className="font-semibold text-accent">Go to dashboard</Link>} />;
}
