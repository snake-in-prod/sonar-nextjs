'use client';

const Profile = async ({ id }: { id: string }) => { // Noncompliant
  const user = await fetch(`/api/users/${id}`).then((r) => r.json());
  return <p>{user.name}</p>;
};

export default Profile;
